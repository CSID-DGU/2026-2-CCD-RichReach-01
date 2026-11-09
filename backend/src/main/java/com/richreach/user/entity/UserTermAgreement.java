package com.richreach.user.entity;

import com.richreach.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 어떤 약관의 어떤 버전에 언제 동의했는지. 한 번 남기면 고치지 않는다. */
@Entity
@Table(name = "user_term_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTermAgreement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** {@link TermType}의 이름. enum 컬럼 대신 문자열로 저장해서 종류를 추가할 때 스키마를 바꾸지 않는다. */
    @Column(name = "term_type", nullable = false, length = 30)
    private String termType;

    @Column(name = "term_version", nullable = false, length = 20)
    private String termVersion;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    public UserTermAgreement(Long userId, TermType termType, String termVersion, LocalDateTime agreedAt) {
        this.userId = userId;
        this.termType = termType.name();
        this.termVersion = termVersion;
        this.agreedAt = agreedAt;
    }

}
