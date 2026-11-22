package com.richreach.card.staging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 카드 이미지 주소 CSV({@value #CARD_IMAGES_FILE})를 읽어 카드 상품({@code card_products})의 {@code image_url}을 채운다.
 * 적재할 때 스테이징 폴더에 이 파일을 함께 두면 {@link StagingRunner}가 승격이 끝난 뒤 실행한다. 파일이 없으면 아무것도 하지
 * 않는다.
 *
 * <ul>
 *   <li><b>카드 앞면 공식 이미지만</b> 쓴다. {@code image_type=CARD_FRONT}이고 {@code url_status=VERIFIED_IMAGE}이며 주소가
 *       {@code https://}로 시작하는 행이다. 제3자 게시물의 이미지({@code THIRD_PARTY_CARD_FRONT})는 사용 권리를 확인하지 못해서
 *       넣지 않는다. 그 카드는 이미지 없이({@code null}) 두고, 앱이 기본 이미지를 보여준다.</li>
 *   <li>카드는 {@code issuer_code}와 {@code card_id}로 찾는다. 카드 상품에 없는 카드(승격하지 않은 카드)는 건너뛴다. 나중에
 *       승격한 뒤 다시 적재하면 채워진다.</li>
 *   <li>같은 파일을 여러 번 적재해도 결과가 같다. 파일에 없는 카드의 기존 이미지 주소는 지우지 않는다.</li>
 *   <li>전체가 한 트랜잭션이라 중간에 실패하면 아무것도 반영되지 않는다.</li>
 * </ul>
 * 이미지 파일이 아니라 카드사 서버의 주소만 저장한다. 카드사가 주소를 바꾸면 이미지가 깨질 수 있다.
 * 로그에는 건수만 남긴다.
 */
@Slf4j
@Component
public class CardImageLoader {

    /** 스테이징 폴더 안에서 카드 이미지 주소를 읽는 파일. */
    public static final String CARD_IMAGES_FILE = "card_images.csv";

    static final String OFFICIAL_IMAGE_TYPE = "CARD_FRONT";
    static final String VERIFIED_STATUS = "VERIFIED_IMAGE";
    static final String HTTPS_PREFIX = "https://";
    /** card_products.image_url 컬럼 길이. */
    static final int MAX_URL_LENGTH = 500;

    private static final List<String> REQUIRED_COLUMNS =
        List.of("issuer_code", "card_id", "image_url", "image_type", "url_status");

    /**
     * @param applied        파일을 읽어 반영했는지. 파일이 없으면 false
     * @param updated        카드 상품에 이미지 주소를 반영한 카드 수
     * @param notInProducts  카드 상품에 없어 건너뛴 행 수(승격하지 않은 카드)
     * @param skippedUnusable 공식 앞면 이미지가 아니거나 주소가 올바르지 않아 쓰지 않은 행 수
     */
    public record Result(boolean applied, int updated, int notInProducts, int skippedUnusable) {

    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    CardImageLoader(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 파일이 없으면 아무것도 바꾸지 않고 {@code applied=false}를 돌려준다. 필수 컬럼이 없으면 예외로 중단한다. */
    public Result load(Path csvFile) {
        if (!Files.isRegularFile(csvFile)) {
            log.info("[card-images] {}가 없어 카드 이미지를 건드리지 않습니다.", csvFile.getFileName());
            return new Result(false, 0, 0, 0);
        }
        Result result = tx.execute(status -> {
            int updated = 0;
            int notInProducts = 0;
            int unusable = 0;
            try (CSVParser parser = CsvStagingLoader.openCsv(csvFile)) {
                List<String> header = parser.getHeaderNames();
                for (String column : REQUIRED_COLUMNS) {
                    if (!header.contains(column)) {
                        throw new IllegalStateException(csvFile.getFileName() + "에 필수 컬럼이 없습니다: " + column);
                    }
                }
                for (CSVRecord record : parser) {
                    String issuerCode = record.get("issuer_code").strip();
                    String cardId = record.get("card_id").strip();
                    String imageUrl = record.get("image_url").strip();
                    if (!usable(record, issuerCode, cardId, imageUrl)) {
                        unusable++;
                        continue;
                    }
                    int changed = jdbc.update(
                        "UPDATE card_products SET image_url = ? WHERE issuer_code = ? AND card_id = ?",
                        imageUrl, issuerCode, cardId);
                    if (changed == 0) {
                        notInProducts++;
                    } else {
                        updated++;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(csvFile.getFileName() + " 파일을 읽지 못했습니다.", e);
            }
            return new Result(true, updated, notInProducts, unusable);
        });
        log.info("[card-images] 카드 이미지 반영 {}개, 카드 상품에 없어 건너뜀 {}개, 쓰지 않은 행 {}개", result.updated(),
            result.notInProducts(), result.skippedUnusable());
        return result;
    }

    /** 카드 앞면 공식 이미지이고 확인된 https 주소이며 컬럼에 들어가는 길이인 행만 쓴다. */
    private static boolean usable(CSVRecord record, String issuerCode, String cardId, String imageUrl) {
        return !issuerCode.isEmpty() && !cardId.isEmpty()
            && OFFICIAL_IMAGE_TYPE.equals(record.get("image_type").strip())
            && VERIFIED_STATUS.equals(record.get("url_status").strip())
            && imageUrl.regionMatches(true, 0, HTTPS_PREFIX, 0, HTTPS_PREFIX.length())
            && imageUrl.length() > HTTPS_PREFIX.length()
            && imageUrl.length() <= MAX_URL_LENGTH;
    }

}
