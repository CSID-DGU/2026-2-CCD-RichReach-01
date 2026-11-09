package com.richreach.card.staging;

import com.richreach.card.staging.CsvStagingLoader.LoadResult;
import com.richreach.card.staging.StagingValidator.CheckResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구조화 CSV 8종을 스테이징 테이블에 넣고, MVP 카드를 표시하고, 연결 상태를 검증한다.
 *
 * <p>실제로 실행하려면 {@code richreach.staging.load-on-startup=true}와 {@code richreach.staging.csv-dir}가
 * 필요하다 ({@link StagingRunner}). 평소에는 꺼져 있다.
 */
@Slf4j
@Service
public class StagingService {

    /** CSV 폴더 안에서 MVP 카드 목록을 읽는 파일. 없으면 MVP 표시를 건드리지 않는다. */
    static final String MVP_CARDS_FILE = "mvp_cards.csv";

    static final String SELECTED = "SELECTED";
    static final String NOT_SELECTED = "NOT_SELECTED";
    /** 팀원 카드를 한 명의 샘플 사용자가 가진 카드로 본다. 팀원별로 구분하지 않는다. */
    static final String SAMPLE_OWNER = "SAMPLE_USER";
    static final String NO_OWNER = "NOT_PROVIDED";

    /**
     * @param applied   mvp_cards.csv를 읽어 반영했는지. 파일이 없으면 false
     * @param selected  MVP로 표시된 카드 수
     * @param unmatched 카탈로그에서 찾지 못한 행 ("issuer_code/card_id"). 크롤링이 더 필요한 카드다
     */
    public record MvpResult(boolean applied, int selected, List<String> unmatched) {
    }

    public record Report(List<LoadResult> loads, MvpResult mvp, List<CheckResult> checks) {
    }

    private final CsvStagingLoader loader;
    private final StagingValidator validator;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    StagingService(CsvStagingLoader loader, StagingValidator validator, JdbcTemplate jdbc,
                   PlatformTransactionManager transactionManager) {
        this.loader = loader;
        this.validator = validator;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** csvDir 안의 CSV 8종을 모두 적재한다. 파일이 하나라도 없으면 아무것도 넣기 전에 중단한다. */
    public Report loadAll(Path csvDir) {
        for (StagingTable table : StagingTables.ALL) {
            if (!Files.isRegularFile(csvDir.resolve(table.csvFile()))) {
                throw new IllegalStateException("CSV 파일이 없습니다: " + csvDir.resolve(table.csvFile()));
            }
        }
        List<LoadResult> loads = new ArrayList<>();
        for (StagingTable table : StagingTables.ALL) {
            loads.add(loader.load(csvDir.resolve(table.csvFile()), table));
        }
        MvpResult mvp = selectMvpCards(csvDir.resolve(MVP_CARDS_FILE));
        List<CheckResult> checks = validator.validate();
        return new Report(loads, mvp, checks);
    }

    /**
     * mvp_cards.csv(issuer_code, card_id)에 있는 카드만 MVP로 표시한다. 이 파일이 기준이라
     * 파일에서 빠진 카드는 표시가 해제된다. 파일이 없으면 아무것도 바꾸지 않는다.
     */
    MvpResult selectMvpCards(Path mvpFile) {
        if (!Files.isRegularFile(mvpFile)) {
            log.info("[staging] {}가 없어 MVP 표시를 건드리지 않습니다.", mvpFile.getFileName());
            return new MvpResult(false, 0, List.of());
        }
        MvpResult result = tx.execute(status -> {
            jdbc.update("UPDATE stg_card_catalog SET mvp_selected = ?, sample_owner = ?", NOT_SELECTED, NO_OWNER);
            int selected = 0;
            List<String> unmatched = new ArrayList<>();
            try (CSVParser parser = CsvStagingLoader.openCsv(mvpFile)) {
                for (CSVRecord record : parser) {
                    String issuerCode = record.get("issuer_code");
                    String cardId = record.get("card_id");
                    int updated = jdbc.update(
                        "UPDATE stg_card_catalog SET mvp_selected = ?, sample_owner = ? WHERE issuer_code = ? AND card_id = ?",
                        SELECTED, SAMPLE_OWNER, issuerCode, cardId);
                    if (updated == 0) {
                        unmatched.add(issuerCode + "/" + cardId);
                    } else {
                        selected++;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(mvpFile + " 파일을 읽지 못했습니다.", e);
            }
            return new MvpResult(true, selected, unmatched);
        });
        log.info("[staging] MVP 카드 {}개 표시, 카탈로그에서 못 찾은 카드 {}개 {}",
            result.selected(), result.unmatched().size(), result.unmatched());
        return result;
    }
}
