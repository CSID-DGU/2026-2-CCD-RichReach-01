package com.richreach.card.staging;

import com.richreach.card.service.CardPromotionService;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 앱을 켤 때 CSV를 한 번 적재한다. 평소에는 꺼져 있고, 적재할 때만 환경변수로 켠다.
 * <pre>
 * STAGING_LOAD_ON_STARTUP=true
 * STAGING_CSV_DIR=C:\경로\319종_카드혜택_전체자료
 * </pre>
 * CSV를 스테이징 테이블에 넣고, MVP 카드를 표시하고, 그 카드만 최종 테이블로 옮긴다.
 * 다 넣었으면 STAGING_LOAD_ON_STARTUP을 다시 끈다. (켜 둬도 upsert라서 중복은 생기지 않지만 매번 시간이 든다.)
 */
@Component
@ConditionalOnProperty(name = "richreach.staging.load-on-startup", havingValue = "true")
class StagingRunner implements ApplicationRunner {

    private final StagingService stagingService;
    private final CardPromotionService promotionService;
    private final String csvDir;

    StagingRunner(StagingService stagingService, CardPromotionService promotionService,
                  @Value("${richreach.staging.csv-dir:}") String csvDir) {
        this.stagingService = stagingService;
        this.promotionService = promotionService;
        this.csvDir = csvDir;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (csvDir.isBlank()) {
            throw new IllegalStateException(
                "richreach.staging.load-on-startup이 켜져 있지만 richreach.staging.csv-dir(STAGING_CSV_DIR)가 비어 있습니다.");
        }
        stagingService.loadAll(Path.of(csvDir));
        // MVP로 표시된 카드만 최종 테이블로 옮긴다. mvp_cards.csv가 없으면 옮길 카드가 없어 아무것도 바뀌지 않는다.
        promotionService.promote();
    }
}
