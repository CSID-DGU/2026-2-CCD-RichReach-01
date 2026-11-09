package com.richreach.codef.service;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.codef.client.CodefApprovalClient;
import com.richreach.codef.client.CodefApprovalClient.ApprovalListResult;
import com.richreach.codef.client.CodefApprovalClient.CodefApproval;
import com.richreach.codef.dto.ApprovalImportResponse;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.service.CodefApprovalTargets.Period;
import com.richreach.codef.service.CodefApprovalTargets.Target;
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.transaction.service.TransactionStoreService.SaveResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * [임시] 승인내역을 가져와 거래로 저장한다. 동기화에 연결하기 전에 변환과 저장을 확인하려는 개발용 호출이며, 확인이 끝나면 삭제한다.
 *
 * <p>승인내역에는 카드 이름이 없고 카드 번호도 일부가 가려져 있어서, 같은 카드사의 보유카드 중 <b>카드 번호의 마지막 4자리 숫자가
 * 같은 카드가 하나일 때만</b> 그 카드의 거래로 저장한다. 하나로 정하지 못한 항목은 저장하지 않고 건수만 센다.
 * 같은 거래를 다시 가져와도 거래 저장이 중복을 막는다. 가맹점명, 금액, 카드번호는 응답과 로그에 남기지 않는다.
 */
@Slf4j
@Profile("local")
@Service
@RequiredArgsConstructor
public class CodefApprovalImportService {

    private final CodefApprovalTargets targets;
    private final CodefApprovalClient approvals;
    private final OwnedCardQueryService ownedCards;
    private final TransactionStoreService store;

    public ApprovalImportResponse importApprovals(long userId, Long accountId, LocalDate from, LocalDate to) {
        Period period = targets.period(from, to);
        Target target = targets.find(userId, accountId);
        ApprovalListResult result = approvals.fetchApprovals(target.connectedId(), target.organization(), period.from(),
            period.to());
        if (!result.success()) {
            log.info("승인내역 가져오기 실패: accountId={}, codefCode={}", target.accountId(), result.code());
            return new ApprovalImportResponse(false, result.code(), period.from(), period.to(), 0, 0, 0, 0, 0, 0, 0, 0);
        }

        CardCompany company = CardCompany.fromOrganizationCode(target.organization());
        Map<String, List<OwnedCardResponse>> cardsByLastFour = new LinkedHashMap<>();
        for (OwnedCardResponse card : ownedCards.list(userId)) {
            if (card.cardCompany() == company) {
                cardsByLastFour.computeIfAbsent(lastFour(card.maskedNumber()), key -> new ArrayList<>()).add(card);
            }
        }

        Map<Long, List<CollectedTransaction>> byCard = new LinkedHashMap<>();
        int unmatched = 0;
        for (CodefApproval approval : result.approvals()) {
            List<OwnedCardResponse> candidates = cardsByLastFour.getOrDefault(lastFour(approval.cardNumber()), List.of());
            if (candidates.size() != 1) {
                unmatched++;
                continue;
            }
            byCard.computeIfAbsent(candidates.get(0).cardId(), key -> new ArrayList<>())
                .add(CodefTransactionMapper.toCollected(approval));
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int skipped = 0;
        int matched = 0;
        for (Map.Entry<Long, List<CollectedTransaction>> entry : byCard.entrySet()) {
            SaveResult saved = store.saveCollected(userId, entry.getKey(), entry.getValue());
            matched += entry.getValue().size();
            created += saved.created();
            updated += saved.updated();
            unchanged += saved.unchanged();
            skipped += saved.skipped();
        }

        // 값은 남기지 않고 건수만 남긴다.
        log.info("승인내역 가져오기: accountId={}, 응답={}건, 읽음={}건, 카드 찾음={}건, 못 찾음={}건, 신규={}, 갱신={}, 그대로={}, 건너뜀={}",
            target.accountId(), result.approvalCount(), result.approvals().size(), matched, unmatched, created, updated,
            unchanged, skipped);
        return new ApprovalImportResponse(true, result.code(), period.from(), period.to(), result.approvalCount(),
            result.approvals().size(), matched, unmatched, created, updated, unchanged, skipped);
    }

    /** 카드 번호의 마지막 4자리 숫자. 가려진 별표와 구분자는 무시하고 숫자가 4자리보다 적으면 있는 만큼, 없으면 빈 문자열이다. */
    static String lastFour(String number) {
        String digits = number == null ? "" : number.replaceAll("\\D", "");
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

}
