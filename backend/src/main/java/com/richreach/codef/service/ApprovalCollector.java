package com.richreach.codef.service;

import com.richreach.card.dto.MatchableCard;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.codef.client.CodefApprovalClient;
import com.richreach.codef.client.CodefApprovalClient.ApprovalListResult;
import com.richreach.codef.client.CodefApprovalClient.CodefApproval;
import com.richreach.codef.config.CodefCollectProperties;
import com.richreach.codef.entity.CardCompany;
import com.richreach.global.exception.BusinessException;
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.transaction.service.TransactionStoreService.SaveResult;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 카드사 계정 하나의 승인내역을 최근 몇 개월({@code codef.collect.months}) 동안 월 단위로 가져와 거래로 저장한다.
 * 카드 동기화 작업({@link CardSyncWorker})이 카드 목록을 저장한 뒤에 부른다.
 *
 * <p>월 단위로 나눠 부르는 것은 카드사마다 한 번에 조회할 수 있는 기간이 달라서다. 가장 최근 달부터 가져오고, 한 달이라도
 * 실패하면(카드사 오류, 제한 시간) 더 오래된 달은 시도하지 않는다. 계정 제한 같은 오류에서 호출을 더 보내지 않으려는 것이다.
 * 이때 이미 가져온 달은 그대로 저장되어 있고 결과는 {@code PARTIAL}이다. 같은 거래를 다시 가져와도 거래 저장이 중복을 막으므로
 * 다음 동기화가 부족한 부분을 채운다.
 *
 * <p>승인내역에는 카드 이름이 없고 카드 번호도 일부가 가려져 있어서, 같은 카드사의 보유카드 중 카드 번호가 충돌하지 않는
 * (양쪽 다 숫자인 자리가 모두 같은) 카드가 <b>하나일 때만</b> 그 카드의 거래로 저장한다. 가려진 자리는 비교하지 않는다.
 * 하나로 정하지 못한 항목은 저장하지 않고 건수만 센다.
 * 이 클래스는 예외를 밖으로 던지지 않는다. 수집이 실패해도 이미 저장한 카드 동기화는 성공으로 남겨야 해서다.
 * 가맹점명, 금액, 카드번호는 로그에 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalCollector {

    public static final String SUCCESS = "SUCCESS";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";

    private final CodefApprovalClient approvals;
    private final OwnedCardQueryService ownedCards;
    private final TransactionStoreService store;
    private final CodefCollectProperties properties;
    private final Clock clock;

    /**
     * 수집 결과.
     *
     * @param status    SUCCESS(모든 달 수집), PARTIAL(일부 달만), FAILED(하나도 못 가져옴)
     * @param saved     새로 저장하거나 갱신한 거래 수
     * @param unmatched 보유카드를 정하지 못해 저장하지 않은 승인내역 수
     * @param from      수집을 마친 기간의 시작일(하나도 못 마쳤으면 null)
     * @param to        수집을 마친 기간의 종료일(하나도 못 마쳤으면 null)
     */
    public record Result(String status, int saved, int unmatched, LocalDate from, LocalDate to) {

    }

    /**
     * @param deadline 이 시각이 지나면 더 부르지 않고 지금까지의 결과로 끝낸다. (동기화 제한 시간 안에 마치려는 것이다)
     */
    public Result collect(long userId, CardCompany company, String connectedId, String organization,
                          LocalDateTime deadline) {
        LocalDate today = LocalDate.now(clock);
        List<MatchableCard> candidates;
        try {
            candidates = ownedCards.listForMatching(userId).stream().filter(card -> card.cardCompany() == company).toList();
        } catch (RuntimeException e) {
            log.error("승인내역 수집 중 보유카드를 읽지 못했습니다: type={}", e.getClass().getSimpleName());
            return new Result(FAILED, 0, 0, null, null);
        }
        int months = properties.months();
        int saved = 0;
        int unmatched = 0;
        int collectedMonths = 0;
        LocalDate collectedFrom = null;
        LocalDate collectedTo = null;
        for (int i = 0; i < months; i++) {
            if (LocalDateTime.now(clock).isAfter(deadline)) {
                log.warn("승인내역 수집이 제한 시간에 가까워 멈춥니다: 수집한 달={}/{}", collectedMonths, months);
                break;
            }
            YearMonth month = YearMonth.from(today).minusMonths(i);
            LocalDate from = month.atDay(1);
            LocalDate to = i == 0 ? today : month.atEndOfMonth();
            try {
                ApprovalListResult result = approvals.fetchApprovals(connectedId, organization, from, to);
                if (!result.success()) {
                    log.warn("승인내역 수집 실패: codefCode={}, 수집한 달={}/{}", result.code(), collectedMonths, months);
                    break;
                }
                Map<Long, List<CollectedTransaction>> byCard = new LinkedHashMap<>();
                for (CodefApproval approval : result.approvals()) {
                    List<MatchableCard> matches = candidates.stream()
                        .filter(card -> isCompatible(card.number(), approval.cardNumber())).toList();
                    if (matches.size() != 1) {
                        unmatched++;
                        continue;
                    }
                    byCard.computeIfAbsent(matches.get(0).cardId(), key -> new ArrayList<>())
                        .add(CodefTransactionMapper.toCollected(approval));
                }
                for (Map.Entry<Long, List<CollectedTransaction>> entry : byCard.entrySet()) {
                    SaveResult result2 = store.saveCollected(userId, entry.getKey(), entry.getValue());
                    saved += result2.created() + result2.updated();
                }
            } catch (BusinessException e) {
                log.warn("승인내역 수집 중 CODEF 호출 실패: error={}, 수집한 달={}/{}", e.getErrorCode().getCode(),
                    collectedMonths, months);
                break;
            } catch (RuntimeException e) {
                // 메시지에 외부 응답이 섞일 수 있어서 종류만 남긴다.
                log.error("승인내역 수집 중 예상하지 못한 오류: type={}, 수집한 달={}/{}", e.getClass().getSimpleName(),
                    collectedMonths, months);
                break;
            }
            collectedMonths++;
            collectedFrom = from;
            if (collectedTo == null) {
                collectedTo = to;
            }
        }

        String status = collectedMonths == 0 ? FAILED : collectedMonths == months ? SUCCESS : PARTIAL;
        // 건수만 남긴다.
        log.info("승인내역 수집: 상태={}, 수집한 달={}/{}, 저장={}건, 카드 못 찾음={}건", status, collectedMonths, months, saved,
            unmatched);
        return new Result(status, saved, unmatched, collectedFrom, collectedTo);
    }

    /**
     * 두 카드 번호가 같은 카드일 수 있는지. 하이픈과 공백을 없애고 끝을 맞춰서 한 자리씩 비교하며, 양쪽 다 숫자인 자리가
     * 다르면 다른 카드다. 가려진 자리(별표)나 없는 자리는 비교하지 않는다. 카드사마다 가리는 자리가 달라서(예: 신한은 끝자리
     * 하나도 가려진다) 끝 4자리가 정확히 같을 때만 보면 짝지을 수 없는 카드가 생긴다.
     *
     * <p>다른 카드와 충돌하지 않는다는 뜻이지 같은 카드라는 확정은 아니다. 후보가 하나일 때만 그 카드로 정하므로, 가려진
     * 자리가 많아 여러 카드와 맞을 수 있으면 정하지 않는다.
     */
    static boolean isCompatible(String ownedNumber, String approvalNumber) {
        String owned = compact(ownedNumber);
        String approval = compact(approvalNumber);
        int length = Math.min(owned.length(), approval.length());
        for (int i = 1; i <= length; i++) {
            char left = owned.charAt(owned.length() - i);
            char right = approval.charAt(approval.length() - i);
            if (Character.isDigit(left) && Character.isDigit(right) && left != right) {
                return false;
            }
        }
        return true;
    }

    private static String compact(String number) {
        return number == null ? "" : number.replaceAll("[\s-]", "");
    }

}
