package com.richreach.benefit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 혜택 계산 설정 ({@code richreach.benefit.*}).
 *
 * @param engine        쓸 계산기 이름. {@code simple}(기본)이 임시 계산기다. 다른 구현을 꽂으면 그 이름으로 바꾼다.
 * @param ruleSelection 임시 계산기가 쓸 규칙. {@code READY_ONLY}(기본)는 사람이 원문과 대조해 확인한 규칙만,
 *                      {@code ALL}은 확인 전 규칙까지 쓴다. ALL은 개발·시연용이고 결과가 참고용이다.
 */
@ConfigurationProperties("richreach.benefit")
public record BenefitProperties(@DefaultValue("simple") String engine,
                                @DefaultValue("READY_ONLY") RuleSelection ruleSelection) {

    public enum RuleSelection {
        READY_ONLY,
        ALL
    }

}
