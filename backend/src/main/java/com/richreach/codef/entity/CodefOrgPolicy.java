package com.richreach.codef.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기관(카드사)코드 + API 종류별 CODEF 연동 정책. 사용자 연동과 무관한 기준정보. */
@Entity
@Table(name = "codef_org_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodefOrgPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String organization;

    @Column(name = "api_type", nullable = false, length = 30)
    private String apiType;

    @Column(name = "max_lookback_months")
    private Integer maxLookbackMonths;

    @Column(name = "period_rule", length = 30)
    private String periodRule;

    @Column(name = "requires_card_auth", nullable = false)
    private boolean requiresCardAuth;

    @Column(name = "sms_auth_risk", nullable = false)
    private boolean smsAuthRisk;

    @Column(name = "preferred_login_type", length = 10)
    private String preferredLoginType;

    @Column(name = "provides_card_no", nullable = false)
    private boolean providesCardNo;

    @Column(name = "provides_standard_amt", nullable = false)
    private boolean providesStandardAmt;

    @Column(name = "provides_required_amt", nullable = false)
    private boolean providesRequiredAmt;

    @Column(name = "provides_comm_period", nullable = false)
    private boolean providesCommPeriod;

    @Column(name = "mvp_supported", nullable = false)
    private boolean mvpSupported;

    @Column(length = 255)
    private String note;

}
