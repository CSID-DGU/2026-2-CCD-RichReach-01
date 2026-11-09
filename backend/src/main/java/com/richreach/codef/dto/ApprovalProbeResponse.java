package com.richreach.codef.dto;

import java.time.LocalDate;

/**
 * [임시] 승인내역 확인 호출의 결과. 소비 정보(가맹점명, 금액)는 담지 않고 건수와 결과 코드만 담는다.
 *
 * @param success       CODEF 결과 코드가 CF-00000인지
 * @param code          CODEF 결과 코드
 * @param from          조회 시작일
 * @param to            조회 종료일
 * @param approvalCount 응답에 들어 있던 승인내역 수
 * @param readCount     거래로 읽어낸 수. 응답 수보다 적으면 필드 이름이나 형식이 예상과 다르다는 뜻이다.
 */
public record ApprovalProbeResponse(boolean success, String code, LocalDate from, LocalDate to, int approvalCount,
                                    int readCount) {

}
