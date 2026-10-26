package com.richreach.card.staging;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CSV 한 파일을 스테이징 테이블에 넣는다. 파일 하나가 한 트랜잭션이라 중간에 실패하면 그 파일은 반영되지 않는다.
 *
 * <p>같은 키가 이미 있으면 덮어쓴다(upsert). 그래서 여러 번 실행해도 중복이 생기지 않고,
 * 다른 카드사 CSV를 나중에 추가로 넣어도 기존 행은 건드리지 않는다.
 */
@Slf4j
@Component
class CsvStagingLoader {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    CsvStagingLoader(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * @param fileRows       CSV의 데이터 행 수(헤더 제외)
     * @param tableRows      적재 후 테이블 전체 행 수. 이미 있던 행이 있으면 fileRows보다 클 수 있다
     * @param duplicateKeys  CSV 안에서 키가 겹친 행 수. upsert 때문에 마지막 행만 남는다
     */
    record LoadResult(String csvFile, String table, int fileRows, long tableRows, int duplicateKeys) {
    }

    LoadResult load(Path csvFile, StagingTable table) {
        LoadResult result = tx.execute(status -> doLoad(csvFile, table));
        log.info("[staging] {} -> {}: 파일 {}행, 테이블 {}행, 파일 내 중복 키 {}건",
            table.csvFile(), table.table(), result.fileRows(), result.tableRows(), result.duplicateKeys());
        if (result.duplicateKeys() > 0) {
            log.warn("[staging] {}에 같은 키가 {}건 겹칩니다. 마지막 행만 남습니다.", table.csvFile(), result.duplicateKeys());
        }
        return result;
    }

    /** BOM을 건너뛰고 UTF-8로 CSV를 연다. 첫 행은 헤더다. */
    static CSVParser openCsv(Path path) throws IOException {
        BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        try {
            reader.mark(1);
            if (reader.read() != 0xFEFF) {
                reader.reset();
            }
            return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader);
        } catch (IOException | RuntimeException e) {
            reader.close();
            throw e;
        }
    }

    private LoadResult doLoad(Path csvFile, StagingTable table) {
        try (CSVParser parser = openCsv(csvFile)) {
            requireColumns(parser.getHeaderMap().keySet(), table);
            String sql = upsertSql(table);
            Set<String> seenKeys = new HashSet<>();
            List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
            int rows = 0;
            int duplicateKeys = 0;
            for (CSVRecord record : parser) {
                if (!record.isConsistent()) {
                    throw new IllegalStateException("%s %d번째 행의 컬럼 수가 헤더와 다릅니다."
                        .formatted(table.csvFile(), record.getRecordNumber()));
                }
                rows++;
                if (!seenKeys.add(keyOf(record, table))) {
                    duplicateKeys++;
                }
                batch.add(toParams(record, table));
                if (batch.size() == BATCH_SIZE) {
                    flush(sql, batch, table);
                }
            }
            flush(sql, batch, table);
            Long tableRows = jdbc.queryForObject("SELECT COUNT(*) FROM `" + table.table() + "`", Long.class);
            return new LoadResult(table.csvFile(), table.table(), rows, tableRows == null ? 0 : tableRows, duplicateKeys);
        } catch (IOException e) {
            throw new UncheckedIOException(table.csvFile() + " 파일을 읽지 못했습니다: " + csvFile, e);
        }
    }

    private void flush(String sql, List<Object[]> batch, StagingTable table) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            jdbc.batchUpdate(sql, batch);
        } catch (DataAccessException e) {
            throw new IllegalStateException("%s 적재 중 DB 오류가 났습니다 (이 묶음 %d행): %s"
                .formatted(table.csvFile(), batch.size(), e.getMostSpecificCause().getMessage()), e);
        }
        batch.clear();
    }

    private static void requireColumns(Set<String> header, StagingTable table) {
        List<String> missing = table.columns().stream().filter(c -> !header.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("%s에 필요한 컬럼이 없습니다: %s".formatted(table.csvFile(), missing));
        }
    }

    /** 같은 키가 있으면 키가 아닌 컬럼을 새 값으로 바꾼다. */
    private static String upsertSql(StagingTable table) {
        String columns = table.columns().stream().map(c -> "`" + c + "`").collect(Collectors.joining(", "));
        String placeholders = "?" + ", ?".repeat(table.columns().size() - 1);
        String updates = table.columns().stream()
            .filter(c -> !table.keys().contains(c))
            .map(c -> "`%s` = new.`%s`".formatted(c, c))
            .collect(Collectors.joining(", "));
        return "INSERT INTO `%s` (%s) VALUES (%s) AS new ON DUPLICATE KEY UPDATE %s"
            .formatted(table.table(), columns, placeholders, updates);
    }

    private static String keyOf(CSVRecord record, StagingTable table) {
        return table.keys().stream().map(record::get).collect(Collectors.joining("\u0000"));
    }

    private static Object[] toParams(CSVRecord record, StagingTable table) {
        Object[] params = new Object[table.columns().size()];
        for (int i = 0; i < params.length; i++) {
            String column = table.columns().get(i);
            // target_code는 비어 있는 게 정상이라 NULL이 아니라 ''로 넣는다. MySQL은 NULL이 든 UNIQUE 키를 서로 다르다고 봐서,
            // NULL로 두면 같은 CSV를 다시 적재할 때 같은 줄이 중복된다. (target_names의 코드 없는 연결)
            // 다른 키(card_id, benefit_id 등)가 비면 NULL이 되어 NOT NULL 제약으로 거부된다.
            if (record.get(column).isEmpty() && table.keys().contains(column) && "target_code".equals(column)) {
                params[i] = "";
                continue;
            }
            try {
                params[i] = convert(record.get(column), table.typeOf(column));
            } catch (RuntimeException e) {
                throw new IllegalStateException("%s %d번째 행 '%s' 값을 변환하지 못했습니다: '%s'"
                    .formatted(table.csvFile(), record.getRecordNumber(), column, record.get(column)), e);
            }
        }
        return params;
    }

    /** 빈칸은 NULL이다(target_code 제외). 0이나 '무제한'으로 바꾸지 않는다. */
    private static Object convert(String value, StagingTable.ColumnType type) {
        if (value.isEmpty()) {
            return null;
        }
        return switch (type) {
            case STRING -> value;
            case INT -> new BigDecimal(value).intValueExact();
            case LONG -> new BigDecimal(value).longValueExact();
            case DECIMAL -> new BigDecimal(value);
            case DATE -> LocalDate.parse(value);
            case BOOL -> switch (value.toLowerCase()) {
                case "true" -> true;
                case "false" -> false;
                default -> throw new IllegalArgumentException("true/false가 아닙니다");
            };
        };
    }
}
