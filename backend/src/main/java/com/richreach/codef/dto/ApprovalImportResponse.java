package com.richreach.codef.dto;

import java.time.LocalDate;

/**
 * [임시] 승인내역 가져오기 결과. 소비 정보(가맹점명, 금액, 카드번호)는 담지 않고 건수만 담는다.
 *
 * @param success        CODEF 결과 코드가 CF-00000인지
 * @param code           CODEF 결과 코드
 * @param from           조회 시작일
 * @param to             조회 종료일
 * @param approvalCount  응답에 들어 있던 승인내역 수
 * @param readCount      거래로 읽어낸 수
 * @param matchedCount   보유카드를 찾아 저장을 시도한 수
 * @param unmatchedCount 보유카드를 하나로 정하지 못해 저장하지 않은 수 (카드 번호가 맞는 카드가 없거나 둘 이상)
 * @param created        새로 저장한 거래
 * @param updated        이미 있던 거래 중 바뀐 것
 * @param unchanged      이미 있던 거래 중 바뀌지 않은 것
 * @param skipped        저장하지 못한 항목
 */
public record ApprovalImportResponse(boolean success, String code, LocalDate from, LocalDate to, int approvalCount,
                                     int readCount, int matchedCount, int unmatchedCount, int created, int updated,
                                     int unchanged, int skipped) {

}
