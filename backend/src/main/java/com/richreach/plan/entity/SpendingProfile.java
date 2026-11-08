package com.richreach.plan.entity;

import com.richreach.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대상 월의 예상 소비 프로필. 소비 정보라서 {@code toString}을 만들지 않는다.
 * 사용자는 다른 도메인이라 ID만 가진다.
 */
@Entity
@Table(name = "spending_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpendingProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 예측 대상 월(yyyy-MM) */
    @Column(name = "target_month", nullable = false, updatable = false, length = 7, columnDefinition = "CHAR(7)")
    private String targetMonth;

    @Column(name = "source_start", nullable = false, updatable = false)
    private LocalDate sourceStart;

    @Column(name = "source_end", nullable = false, updatable = false)
    private LocalDate sourceEnd;

    public static SpendingProfile create(Long userId, YearMonth targetMonth, LocalDate sourceStart,
                                         LocalDate sourceEnd) {
        if (sourceEnd.isBefore(sourceStart)) {
            throw new IllegalArgumentException("소비 기간의 종료일이 시작일보다 빠릅니다.");
        }
        SpendingProfile profile = new SpendingProfile();
        profile.userId = userId;
        profile.targetMonth = targetMonth.toString();
        profile.sourceStart = sourceStart;
        profile.sourceEnd = sourceEnd;
        return profile;
    }
}
