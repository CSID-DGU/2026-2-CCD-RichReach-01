package com.richreach.codef.service;

import com.richreach.card.dto.MatchableCard;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.codef.client.CodefApprovalClient;
import com.richreach.codef.client.CodefApprovalClient.ApprovalListResult;
import com.richreach.codef.client.CodefApprovalClient.CodefApproval;
import com.richreach.codef.config.CodefCollectProperties;
import com.richreach.codef.entity.CardCompany;
import com.richreach.global.event.TransactionsChangedEvent;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 카드사 계정 하나의 승인내역을 최근 몇 개월({@code codef.collect.months}) 동안 월 단위로 가져와 거래로 저장한다.
 * 카드 동기화 작업({@link CardSyncWorker})이 카드 목록을 저장한 뒤에 부른다.
 *
 * <p>월 단위로 나눠 부르는 것은 카드사마다 한 번에 조회할 수 있는 기간이 달라서다. 가장 최근 달부터 가져오고, 한 달이라도
 * 실패하면(카드사 오류, 제한 시간) 더 오래된 달은 시도하지 않는다. 계정 제한 같은 오류에서 호출을 더 보내지 않으려는 것이다.
 * 이때 이미 가져온 달은 그대로 저장되어 있고 결과는 {@code PARTIAL}이다. 같은 거래를 다시 가져와도 거래 저장이 중복을 막는다.
 *
 * <p><b>수집 범위 기록과 증분 수집.</b> 수집에 성공한 연속된 기간을 {@link Range}로 돌려주고, 호출하는 쪽이 카드사 계정에
 * 기록한다. 다음 동기화는 그 기록을 {@code previous}로 넘겨서, 이미 수집한 오래된 달은 건너뛰고 <b>마지막으로 수집한 달부터
 * 이번 달까지</b>만 다시 가져온다. (마지막 달은 그 뒤에 생긴 거래와 취소를 반영하려고 다시 가져온다.) 다음 경우에는 기록을
 * 믿지 않고 전체 기간을 다시 수집한다.
 * <ul>
 *   <li>기록이 없을 때</li>
 *   <li>기록할 때와 보유카드 구성이 다를 때. 새 카드는 이미 수집한 기간에도 거래가 저장되지 않았을 수 있다.</li>
 * </ul>
 * 이어지지 않는 구간(중간 달에서 실패해 생긴 틈)은 기록하지 않는다. 기록은 거래가 없던 기간과 수집하지 못한 기간을 구분하려는 것이다.
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
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * 수집을 마친 연속된 기간.
     *
     * @param from    시작일(포함, 항상 어느 달의 1일)
     * @param to      종료일(포함, 마지막으로 수집한 날)
     * @param cardIds 기록할 때의 보유카드 id 목록(쉼표로 구분). 보유카드 구성이 바뀌었는지 가리는 데 쓴다.
     */
    public record Range(LocalDate from, LocalDate to, String cardIds) {

    }

    /**
     * 수집 결과.
     *
     * @param status    SUCCESS(이번에 가져올 달을 모두 수집), PARTIAL(일부 달만), FAILED(하나도 못 가져옴)
     * @param saved     새로 저장하거나 갱신한 거래 수
     * @param unmatched 보유카드를 정하지 못해 저장하지 않은 승인내역 수
     * @param range     이번 수집 뒤에 기록할 수집 범위. 기록할 구간이 없으면(기존 기록도 쓸 수 없는 경우) null
     */
    public record Result(String status, int saved, int unmatched, Range range) {

        public LocalDate from() {
            return range == null ? null : range.from();
        }

        public LocalDate to() {
            return range == null ? null : range.to();
        }

    }

    /**
     * @param previous 이 카드사 계정에 기록된 수집 범위. 없으면 null
     * @param deadline 이 시각이 지나면 더 부르지 않고 지금까지의 결과로 끝낸다. (동기화 제한 시간 안에 마치려는 것이다)
     */
    public Result collect(long userId, CardCompany company, String connectedId, String organization, Range previous,
                          LocalDateTime deadline) {
        LocalDate today = LocalDate.now(clock);
        List<MatchableCard> candidates;
        try {
            candidates = ownedCards.listForMatching(userId).stream().filter(card -> card.cardCompany() == company).toList();
        } catch (RuntimeException e) {
            log.error("승인내역 수집 중 보유카드를 읽지 못했습니다: type={}", e.getClass().getSimpleName());
            return new Result(FAILED, 0, 0, previous);
        }
        String cardIds = candidates.stream().map(MatchableCard::cardId).sorted().map(String::valueOf)
            .collect(Collectors.joining(","));
        // 카드 구성이 같을 때만 기록을 믿는다.
        Range usable = previous != null && previous.from() != null && previous.to() != null
            && cardIds.equals(previous.cardIds()) ? previous : null;
        YearMonth refreshMonth = usable == null ? null : YearMonth.from(usable.to());

        int months = properties.months();
        int saved = 0;
        int unmatched = 0;
        int succeeded = 0;
        int skipped = 0;
        // 이번에 가져와야 하는 달 수. 이미 수집한 달은 뺀다.
        int expected = 0;
        for (int i = 0; i < months; i++) {
            if (!isCovered(YearMonth.from(today).minusMonths(i), usable, refreshMonth)) {
                expected++;
            }
        }
        // 가장 최근 달부터 끊기지 않고 이어서 처리를 마친(수집했거나 이미 수집한) 가장 오래된 달
        YearMonth coveredOldest = null;
        for (int i = 0; i < months; i++) {
            YearMonth month = YearMonth.from(today).minusMonths(i);
            if (isCovered(month, usable, refreshMonth)) {
                // 마지막으로 수집한 달보다 오래되고 이미 수집한 기간 안이라 다시 가져오지 않는다.
                coveredOldest = month;
                skipped++;
                continue;
            }
            if (LocalDateTime.now(clock).isAfter(deadline)) {
                log.warn("승인내역 수집이 제한 시간에 가까워 멈춥니다: 수집한 달={}/{}", succeeded, expected);
                break;
            }
            LocalDate from = month.atDay(1);
            LocalDate to = i == 0 ? today : month.atEndOfMonth();
            try {
                ApprovalListResult result = approvals.fetchApprovals(connectedId, organization, from, to);
                if (!result.success()) {
                    log.warn("승인내역 수집 실패: codefCode={}, 수집한 달={}/{}", result.code(), succeeded, expected);
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
                    SaveResult saveResult = store.saveCollected(userId, entry.getKey(), entry.getValue());
                    saved += saveResult.created() + saveResult.updated();
                }
            } catch (BusinessException e) {
                log.warn("승인내역 수집 중 CODEF 호출 실패: error={}, 수집한 달={}/{}", e.getErrorCode().getCode(), succeeded,
                    expected);
                break;
            } catch (RuntimeException e) {
                // 메시지에 외부 응답이 섞일 수 있어서 종류만 남긴다.
                log.error("승인내역 수집 중 예상하지 못한 오류: type={}, 수집한 달={}/{}", e.getClass().getSimpleName(), succeeded,
                    expected);
                break;
            }
            succeeded++;
            coveredOldest = month;
        }

        String status = succeeded == 0 ? FAILED : succeeded >= expected ? SUCCESS : PARTIAL;
        Range range = rangeAfter(today, coveredOldest, usable, refreshMonth, cardIds);
        if (saved > 0) {
            // 거래가 새로 저장되거나 갱신되었으니 이번 달 플랜이 있으면 남은 기간을 다시 계산하도록 알린다.
            events.publishEvent(new TransactionsChangedEvent(userId, YearMonth.from(today),
                TransactionsChangedEvent.Reason.SYNCED));
        }
        // 건수만 남긴다.
        log.info("승인내역 수집: 상태={}, 수집한 달={}/{}, 건너뜀={}, 저장={}건, 카드 못 찾음={}건", status, succeeded, expected,
            skipped, saved, unmatched);
        return new Result(status, saved, unmatched, range);
    }

    /** 이미 수집한 기간이라 다시 가져오지 않아도 되는 달인지. 마지막으로 수집한 달보다 오래되고 기록된 시작일 이후인 달이다. */
    private static boolean isCovered(YearMonth month, Range usable, YearMonth refreshMonth) {
        return usable != null && month.isBefore(refreshMonth) && !month.atDay(1).isBefore(usable.from());
    }

    /**
     * 이번 수집 뒤에 기록할 범위. 가장 최근 달부터 이어서 처리를 마친 달이 없으면 기존 기록을 그대로 둔다. 있으면 그 달의 1일부터
     * 오늘까지이고, 기존 기록과 이어지면(마지막으로 수집한 달까지 닿았으면) 기존 시작일까지 합친다.
     */
    private static Range rangeAfter(LocalDate today, YearMonth coveredOldest, Range usable, YearMonth refreshMonth,
                                    String cardIds) {
        if (coveredOldest == null) {
            return usable;
        }
        LocalDate from = coveredOldest.atDay(1);
        boolean connected = usable != null && !coveredOldest.isAfter(refreshMonth);
        if (connected && usable.from().isBefore(from)) {
            from = usable.from();
        }
        return new Range(from, today, cardIds);
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
        return number == null ? "" : number.replaceAll("[\\s-]", "");
    }

}
