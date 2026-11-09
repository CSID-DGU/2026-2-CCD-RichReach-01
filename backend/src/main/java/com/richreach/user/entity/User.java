package com.richreach.user.entity;

import com.richreach.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "user_type", nullable = false, length = 20)
    private String userType;

    @Column(name = "terms_agreed_at", nullable = false)
    private LocalDateTime termsAgreedAt;

    /** 연속 로그인 실패 횟수. 로그인에 성공하면 0으로 되돌린다. */
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    /** 이 시각까지 로그인할 수 없다. null이거나 지났으면 잠기지 않은 상태다. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    public User(String email, String passwordHash, String name, String phone, String userType,
        LocalDateTime termsAgreedAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
        this.userType = userType;
        this.termsAgreedAt = termsAgreedAt;
    }

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * 로그인 실패를 기록한다. {@code maxFailures}번째 실패에서 {@code lockDuration} 동안 잠근다.
     * 이전 잠금이 이미 풀렸다면 실패 횟수를 처음부터 다시 센다.
     */
    public void recordLoginFailure(LocalDateTime now, int maxFailures, Duration lockDuration) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            failedLoginCount = 0;
            lockedUntil = null;
        }
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
        }
    }

    /** 비밀번호를 바꾼다. 호출하는 쪽에서 이미 해시한 값을 넘긴다. */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void resetLoginFailures() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

}
