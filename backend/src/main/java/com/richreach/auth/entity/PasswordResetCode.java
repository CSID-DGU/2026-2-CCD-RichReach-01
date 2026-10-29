package com.richreach.auth.entity;

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

/**
 * 비밀번호 재설정 인증번호. 사용자당 하나이며 다시 발송하면 {@link #renew}로 같은 행을 바꾼다.
 * 인증번호 원문이 아니라 BCrypt 해시만 저장한다.
 */
@Entity
@Table(name = "password_reset_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetCode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    public PasswordResetCode(Long userId, String codeHash, LocalDateTime now, Duration ttl) {
        this.userId = userId;
        renew(codeHash, now, ttl);
    }

    /** 새 인증번호로 바꾼다. 틀린 횟수도 처음부터 다시 센다. */
    public void renew(String codeHash, LocalDateTime now, Duration ttl) {
        this.codeHash = codeHash;
        this.sentAt = now;
        this.expiresAt = now.plus(ttl);
        this.failedAttempts = 0;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /** 마지막 발송 뒤 {@code interval}이 지났을 때만 다시 보낼 수 있다. */
    public boolean canResend(LocalDateTime now, Duration interval) {
        return !sentAt.plus(interval).isAfter(now);
    }

    /** 틀린 횟수가 최대치에 닿아 더는 확인할 수 없는 상태. 행은 남겨 두어 재전송 대기 시간이 계속 적용되게 한다. */
    public boolean isExhausted(int maxAttempts) {
        return failedAttempts >= maxAttempts;
    }

    public int recordFailure() {
        return ++failedAttempts;
    }

}
