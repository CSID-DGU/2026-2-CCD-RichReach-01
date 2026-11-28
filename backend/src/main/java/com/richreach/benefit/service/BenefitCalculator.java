package com.richreach.benefit.service;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.card.dto.CalculationRule;
import java.util.List;

/**
 * 혜택 계산기. 카드 한 장에 한 달 동안 업종별로 이만큼 쓰면 예상 혜택이 얼마인지를 계산한다.
 * 월간 플랜(카드 배분)은 이 인터페이스만 보고 혜택을 구하므로, 계산 방식을 바꿔도 플랜 쪽은 고치지 않는다.
 *
 * <p>구현을 바꾸려면 이 인터페이스를 구현한 빈을 만들고 {@code richreach.benefit.engine}을 그 이름으로 맞춘다.
 * 기본 구현({@link SimpleBenefitCalculator}, 이름 {@code simple})은 임시 계산기다.
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

    /**
     * 호출자가 이미 읽어 둔 카드 규칙으로 계산한다. 한 요청에서 같은 카드를 여러 번 계산할 때 규칙을 반복해서 읽지 않기 위한 것이다.
     * 결과는 {@link #calculate(BenefitQuery)}와 같아야 한다.
     *
     * @param rules 그 카드 상품의 규칙 전체. 승인 여부 등으로 거르지 않고 조회한 그대로 넘긴다.
     *              이 값을 쓰지 않는 구현은 기본 동작대로 스스로 계산한다.
     */
    default BenefitResult calculate(BenefitQuery query, List<CalculationRule> rules) {
        return calculate(query);
    }

}
