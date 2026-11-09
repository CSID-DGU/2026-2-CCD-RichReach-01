package com.richreach.codef.service;

import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * [임시] 승인내역 개발용 호출(확인, 가져오기)이 함께 쓰는 조회 기간 확인과 카드사 계정 선택. 개발용 호출과 함께 삭제한다.
 *
 * <p>호출 횟수 제한과 카드사의 접속 차단 위험이 있어서 조회 기간은 {@value #MAX_DAYS}일 이하, 오늘까지로 제한한다.
 * 계정 제한 중인 계정은 건너뛴다. 카드 비밀번호는 보내지 않는다.
 */
@Profile("local")
@Component
@RequiredArgsConstructor
class CodefApprovalTargets {

    static final int MAX_DAYS = 31;
    static final int DEFAULT_DAYS = 7;

    private final CodefOrgAccountRepository orgAccounts;
    private final TransactionTemplate transaction;
    private final Clock clock;

    /** 조회 기간. */
    record Period(LocalDate from, LocalDate to) {

    }

    /** 조회할 카드사 계정. connectedId는 로그에 남기지 않는다. */
    record Target(Long accountId, String connectedId, String organization) {

        @Override
        public String toString() {
            return "Target[accountId=" + accountId + "]";
        }

    }

    /**
     * @param from 조회 시작일. 비우면 {@code to} 기준 {@value #DEFAULT_DAYS}일 전
     * @param to   조회 종료일. 비우면 오늘
     */
    Period period(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        if (start.isAfter(end) || end.isAfter(today) || ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                "기간은 오늘까지, 최대 " + MAX_DAYS + "일이어야 합니다.");
        }
        return new Period(start, end);
    }

    /** DB에서 읽는 일은 짧은 트랜잭션으로 끝낸다. (카드사 호출은 트랜잭션 밖에서 해야 한다.) */
    Target find(long userId, Long accountId) {
        return transaction.execute(status -> {
            List<CodefOrgAccount> candidates = orgAccounts.findActiveByUserId(userId).stream()
                .filter(account -> !account.isLocked())
                .filter(account -> accountId == null || account.getId().equals(accountId)).toList();
            if (candidates.isEmpty()) {
                throw new BusinessException(ErrorCode.CONNECTION_NOT_FOUND);
            }
            CodefOrgAccount account = candidates.get(0);
            return new Target(account.getId(), account.getConnection().getConnectedId(), account.getOrganization());
        });
    }

}
