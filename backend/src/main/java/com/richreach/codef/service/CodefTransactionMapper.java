package com.richreach.codef.service;

import com.richreach.codef.client.CodefApprovalClient.CodefApproval;
import com.richreach.transaction.dto.CollectedTransaction;
import java.time.LocalTime;

/**
 * CODEF 승인내역 한 건을 거래 저장이 받는 {@link CollectedTransaction}으로 바꾼다. DB 없이 값만으로 바꾸므로 따로 확인할 수 있다.
 *
 * <ul>
 *   <li>승인일시는 이용 일자와 시각이다. 시각이 없으면 그 날 00:00:00이다.</li>
 *   <li>업종은 비워 둔다. 거래 저장이 가맹점명으로 자동 분류한다. (CODEF의 업종 이름은 {@code 결제대행(PG)}처럼 실제 업종을
 *       알려주지 못하는 경우가 많다.)</li>
 *   <li>해외 여부는 국내/해외 구분이 {@code 2}일 때만 해외로 본다. (실제 응답에서 국내는 {@code 1}로 확인했고, 해외 값은 아직
 *       확인하지 못했다.)</li>
 * </ul>
 */
final class CodefTransactionMapper {

    static final String OVERSEAS_CODE = "2";

    private CodefTransactionMapper() {
    }

    static CollectedTransaction toCollected(CodefApproval approval) {
        LocalTime time = approval.usedTime() == null ? LocalTime.MIDNIGHT : approval.usedTime();
        return new CollectedTransaction(approval.approvalNo(), approval.usedDate().atTime(time),
            approval.merchantName(), approval.amount(), approval.canceledAmount(), approval.installmentMonths(),
            OVERSEAS_CODE.equals(approval.homeForeignType()), null);
    }

}
