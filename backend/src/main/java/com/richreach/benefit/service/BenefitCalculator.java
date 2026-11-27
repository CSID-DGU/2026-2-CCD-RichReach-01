package com.richreach.benefit.service;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitPurchase;
import com.richreach.benefit.dto.BenefitResult;
import java.util.List;
import java.util.Map;

/**
 * 혜택 계산기. 기존 업종별 월 합계 계산과 거래별 승인 순서 계산을 제공한다.
 * 거래별 계산은 즉시 추천과 실제 거래 반영 플랜에 사용한다.
 *
 * <p>구현을 바꾸려면 이 인터페이스를 구현한 빈을 만들고 {@code richreach.benefit.engine}을 그 이름으로 맞춘다.
 * 기본 구현({@link SimpleBenefitCalculator}, 이름 {@code simple})을 운영 경로에서 사용한다.
 *
 * <p>구현이 지켜야 할 약속
 * <ul>
 *   <li>같은 입력이면 같은 결과를 낸다. (DB 값이 같다면)</li>
 *   <li>DB를 바꾸지 않는다. 읽기만 한다.</li>
 *   <li>{@code totalBenefit}은 {@code benefitByCategory}의 합과 같고, 모든 금액은 0 이상이다.</li>
 *   <li>카드 상품이 없거나 규칙이 없으면 예외 대신 혜택 0인 결과를 낸다.</li>
 *   <li>소비 금액은 소비 정보라서 로그에 남기지 않는다.</li>
 * </ul>
 */
public interface BenefitCalculator {

    BenefitResult calculate(BenefitQuery query);

    /** Approved purchases and a prospective purchase in approval order. IDs must be unique. */
    default Map<Long, BenefitResult> calculatePurchases(long cardProductId, List<BenefitPurchase> purchases,
                                                        long previousMonthSpend) {
        throw new UnsupportedOperationException("거래별 혜택 계산을 지원하지 않는 계산기입니다.");
    }

}
