package com.richreach.codef.service;

import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 거래 수집 기록을 다른 도메인(거래 요약, 소비 분석)에 알려준다. 카드사 계정의 Entity와 Repository는 이 도메인 밖에서
 * 직접 쓰지 않고 이 서비스를 거친다.
 *
 * <p>카드사 계정마다 수집을 마친 시작일이 다를 수 있다. 거래 요약은 모든 카드사의 거래를 합치므로, <b>가장 늦은 시작일</b>부터가
 * 모든 카드사에서 수집이 끝난 기간이다. 해제하지 않은 카드사 계정 중 하나라도 기록이 없으면(수집 기능 이전의 계정, 아직 수집하지
 * 못한 계정) 전체 기간을 확신할 수 없어서 기록이 없는 것으로 돌려준다. 호출하는 쪽은 그때 기존 추정 방식으로 대신한다.
 */
@Service
@RequiredArgsConstructor
public class CollectionRangeQueryService {

    private final CodefOrgAccountRepository orgAccounts;

    /**
     * 모든 카드사 계정에서 거래 수집을 마친 가장 이른 날짜. 연결한 카드사가 없거나 하나라도 수집 기록이 없으면 비어 있다.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> collectedFrom(long userId) {
        List<CodefOrgAccount> accounts = orgAccounts.findActiveByUserId(userId);
        if (accounts.isEmpty() || accounts.stream().anyMatch(account -> account.getTxCollectedFrom() == null)) {
            return Optional.empty();
        }
        return accounts.stream().map(CodefOrgAccount::getTxCollectedFrom).max(LocalDate::compareTo);
    }

}
