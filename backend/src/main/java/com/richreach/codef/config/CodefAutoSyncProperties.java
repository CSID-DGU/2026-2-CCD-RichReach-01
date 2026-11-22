package com.richreach.codef.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 거래내역 자동 동기화 설정 ({@code codef.auto-sync.*}). 사용자가 카드사 연결마다 켜 둔 자동 동기화
 * ({@code codef_org_accounts.auto_sync_enabled})를 서버가 주기적으로 실행할지와 실행 방식을 정한다.
 *
 * <p>서버가 사용자를 대신해 카드사를 부르는 기능이라, 실패가 반복되면 사용자의 카드사 계정이 잠길 수 있다. 그래서
 * <b>기본값은 꺼짐</b>이고, 서버 환경에서 의도적으로 켠다. 로컬과 테스트에서는 켜지 않는다.
 *
 * @param enabled        true면 서버가 {@code cron}에 따라 자동 동기화를 실행한다. 기본 false.
 * @param cron           실행 시각(스프링 cron, 한국 시간). 기본은 매일 06:00. 하루 한 번을 전제로 한다.
 * @param minInterval    같은 카드사 계정에 이 시간 안에 수집을 시작한 적이 있으면(성공·실패 모두) 이번에는 건너뛴다. 사용자가 방금
 *                       직접 동기화했거나 오늘 이미 실패한 계정을 다시 부르지 않으려는 것이다. 기본 20시간.
 * @param gapBetweenUsers 한 사용자의 동기화가 끝난 뒤 다음 사용자를 시작하기 전에 쉬는 시간. 카드사·CODEF 호출이 몰리지 않게
 *                       한다. 0이면 쉬지 않는다. 기본 5초.
 * @param maxUsersPerRun 한 번 실행에서 처리하는 사용자 수의 상한. 넘는 사용자는 다음 실행으로 넘어간다. 기본 200.
 */
@ConfigurationProperties("codef.auto-sync")
public record CodefAutoSyncProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("0 0 6 * * *") String cron,
    @DefaultValue("20h") Duration minInterval,
    @DefaultValue("5s") Duration gapBetweenUsers,
    @DefaultValue("200") int maxUsersPerRun) {

    public CodefAutoSyncProperties {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("codef.auto-sync.cron은 비어 있을 수 없습니다.");
        }
        if (minInterval == null || minInterval.isZero() || minInterval.isNegative()) {
            throw new IllegalArgumentException("codef.auto-sync.min-interval은 0보다 커야 합니다.");
        }
        if (gapBetweenUsers == null || gapBetweenUsers.isNegative()) {
            throw new IllegalArgumentException("codef.auto-sync.gap-between-users는 0 이상이어야 합니다.");
        }
        if (maxUsersPerRun < 1) {
            throw new IllegalArgumentException("codef.auto-sync.max-users-per-run은 1 이상이어야 합니다.");
        }
    }

}
