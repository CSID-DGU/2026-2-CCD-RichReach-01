package com.richreach.codef.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * [임시] 승인내역 확인 호출의 결과. 소비 정보(가맹점명, 금액, 카드번호)는 담지 않고 건수, 결과 코드, 코드값별 건수만 담는다.
 *
 * @param success          CODEF 결과 코드가 CF-00000인지
 * @param code             CODEF 결과 코드
 * @param from             조회 시작일
 * @param to               조회 종료일
 * @param approvalCount    응답에 들어 있던 승인내역 수
 * @param readCount        거래로 읽어낸 수. 응답 수보다 적으면 필드 이름이나 형식이 예상과 다르다는 뜻이다.
 * @param canceledCount    취소 금액이 0보다 큰 건수
 * @param fullCanceledCount 취소 금액이 승인 금액과 같은(전체 취소) 건수
 * @param codeStats        코드값이 무슨 뜻인지 알아내기 위한 통계. 필드 이름 → (값 → 건수). 취소 구분, 결제 구분, 국내/해외 구분,
 *                         통화, 가맹점 업종처럼 종류가 적은 필드만 담고, 필드마다 값이 {@value #MAX_VALUES}종류를 넘으면 앞쪽만 담는다.
 */
public record ApprovalProbeResponse(boolean success, String code, LocalDate from, LocalDate to, int approvalCount,
                                    int readCount, int canceledCount, int fullCanceledCount,
                                    Map<String, Map<String, Long>> codeStats) {

    public static final int MAX_VALUES = 30;

}
