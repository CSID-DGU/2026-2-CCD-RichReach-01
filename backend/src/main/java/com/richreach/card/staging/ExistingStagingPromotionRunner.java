package com.richreach.card.staging;

import com.richreach.card.service.CardPromotionService;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 기존 DB 스테이징 행을 재적재하지 않고, 명시된 카드만 운영 테이블로 옮기는 일회성 실행기. */
@Slf4j
@Component
@ConditionalOnProperty(name = "richreach.staging.promote-existing-on-startup", havingValue = "true")
class ExistingStagingPromotionRunner implements ApplicationRunner {
    private final StagingService staging;
    private final CardPromotionService promotion;
    private final CardImageLoader imageLoader;
    private final TransactionTemplate tx;
    private final String manifest;
    private final int expectedCards;
    private final boolean loadOnStartup;

    ExistingStagingPromotionRunner(StagingService staging, CardPromotionService promotion,
                                    CardImageLoader imageLoader,
                                    PlatformTransactionManager manager,
                                    @Value("${richreach.staging.promotion-manifest:}") String manifest,
                                    @Value("${richreach.staging.promotion-expected-cards:0}") int expectedCards,
                                    @Value("${richreach.staging.load-on-startup:false}") boolean loadOnStartup) {
        this.staging = staging;
        this.promotion = promotion;
        this.imageLoader = imageLoader;
        this.tx = new TransactionTemplate(manager);
        this.manifest = manifest;
        this.expectedCards = expectedCards;
        this.loadOnStartup = loadOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (loadOnStartup) {
            throw new IllegalStateException("기존 스테이징 승격과 CSV 재적재를 동시에 켤 수 없습니다.");
        }
        if (manifest.isBlank() || expectedCards < 1) {
            throw new IllegalStateException("승격 목록 경로와 예상 카드 수를 지정해야 합니다.");
        }
        CardPromotionService.Result result = tx.execute(status -> {
            staging.selectExistingCardsForPromotion(Path.of(manifest), expectedCards);
            CardPromotionService.Result promoted = promotion.promote();
            imageLoader.load(Path.of(manifest).resolveSibling(CardImageLoader.CARD_IMAGES_FILE));
            return promoted;
        });
        log.info("[promotion-existing] 기존 스테이징 카드 {}장 승격, 운영 카드 {}장, 운영 혜택 {}건. "
                + "rule_engine_ready는 변경하지 않았습니다.",
            result.mvpSelected(), result.cardProducts(), result.benefitRules());
    }
}
