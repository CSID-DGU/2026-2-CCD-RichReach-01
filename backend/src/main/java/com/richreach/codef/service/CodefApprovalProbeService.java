package com.richreach.codef.service;

import com.richreach.codef.client.CodefApprovalClient;
import com.richreach.codef.client.CodefApprovalClient.ApprovalListResult;
import com.richreach.codef.client.CodefApprovalClient.CodefApproval;
import com.richreach.codef.dto.ApprovalProbeResponse;
import com.richreach.codef.service.CodefApprovalTargets.Period;
import com.richreach.codef.service.CodefApprovalTargets.Target;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * [임시] 승인내역 응답 구조를 확인하기 위한 개발용 호출. 확인이 끝나면 삭제한다.
 *
 * <p>내 카드사 연결로 승인내역을 한 번 조회하고 <b>건수, 결과 코드, 코드값별 건수만</b> 돌려준다. 가맹점명, 금액, 카드번호 같은
 * 값은 응답과 로그 어디에도 남기지 않는다. 응답의 구조(필드 이름과 형식)는 {@code CODEF_DEBUG_LOGSHAPE=true}일 때 값 없이
 * 서버 로그로 확인한다. 조회 기간 제한과 계정 선택은 {@link CodefApprovalTargets}가 맡는다.
 */
@Slf4j
@Profile("local")
@Service
@RequiredArgsConstructor
public class CodefApprovalProbeService {

    private final CodefApprovalTargets targets;
    private final CodefApprovalClient approvals;

    /**
     * @param accountId 조회할 카드사 계정 ID. 비우면 제한 중이 아닌 첫 계정
     * @param from      조회 시작일. 비우면 {@code to} 기준 7일 전
     * @param to        조회 종료일. 비우면 오늘
     */
    public ApprovalProbeResponse probe(long userId, Long accountId, LocalDate from, LocalDate to) {
        Period period = targets.period(from, to);
        Target target = targets.find(userId, accountId);
        ApprovalListResult result = approvals.fetchApprovals(target.connectedId(), target.organization(), period.from(),
            period.to());

        // 값은 남기지 않고 건수와 결과 코드만 남긴다.
        log.info("승인내역 확인 호출: accountId={}, codefCode={}, 응답={}건, 읽은 거래={}건", target.accountId(),
            result.code(), result.approvalCount(), result.approvals().size());
        List<CodefApproval> read = result.approvals();
        return new ApprovalProbeResponse(result.success(), result.code(), period.from(), period.to(),
            result.approvalCount(), read.size(), (int) read.stream().filter(a -> a.canceledAmount() > 0).count(),
            (int) read.stream().filter(a -> a.canceledAmount() > 0 && a.canceledAmount() == a.amount()).count(),
            codeStats(read));
    }

    /**
     * 코드값이 무슨 뜻인지 알아내려고 종류가 적은 필드의 값별 건수를 센다. 가맹점명, 금액, 카드번호처럼 개인을 알아볼 수 있는 필드는
     * 세지 않는다. 값은 길이를 자르고, 종류가 많으면 앞쪽만 담는다.
     */
    static Map<String, Map<String, Long>> codeStats(List<CodefApproval> approvals) {
        Map<String, Function<CodefApproval, String>> fields = new LinkedHashMap<>();
        fields.put("cancelStatus", CodefApproval::cancelStatus);
        fields.put("paymentType", CodefApproval::paymentType);
        fields.put("homeForeignType", CodefApproval::homeForeignType);
        fields.put("currency", CodefApproval::currency);
        fields.put("merchantType", CodefApproval::merchantType);
        Map<String, Map<String, Long>> stats = new LinkedHashMap<>();
        fields.forEach((name, getter) -> {
            Map<String, Long> counts = new TreeMap<>();
            for (CodefApproval approval : approvals) {
                String value = getter.apply(approval);
                String key = value.isEmpty() ? "(빈 값)" : value.length() > 20 ? value.substring(0, 20) : value;
                if (counts.containsKey(key) || counts.size() < ApprovalProbeResponse.MAX_VALUES) {
                    counts.merge(key, 1L, Long::sum);
                }
            }
            stats.put(name, counts);
        });
        return stats;
    }

}
