package com.richreach.codef.dto;

import com.richreach.user.entity.TermType;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 카드 연동 동의 결과.
 *
 * @param types        동의가 기록된 항목 (카드정보 조회, 거래내역 조회, 개인정보 수집 및 이용)
 * @param termsVersion 동의가 기록된 약관 버전 (서버가 정한 현재 버전)
 * @param agreedAt     동의 시각. 같은 버전에 다시 동의해도 처음 기록된 시각을 돌려준다.
 */
public record ConnectionAgreementResponse(List<TermType> types, String termsVersion, OffsetDateTime agreedAt) {
}
