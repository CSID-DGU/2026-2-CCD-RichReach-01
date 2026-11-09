package com.richreach.auth.service;

import com.richreach.auth.config.LoginLockProperties;
import com.richreach.auth.dto.LoginRequest;
import com.richreach.auth.dto.TokenPair;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 + 비밀번호 로그인. 연속으로 실패하면 일정 시간 로그인을 막는다.
 *
 * <ul>
 *   <li>계정이 없든 비밀번호가 틀리든 같은 오류({@link ErrorCode#INVALID_CREDENTIALS})를 내려서 가입 여부를 알 수 없게 한다.</li>
 *   <li>계정이 없을 때도 비밀번호 해시를 한 번 계산해서, 응답 시간으로 계정 존재 여부를 짐작하지 못하게 한다.</li>
 *   <li>잠긴 동안에는 비밀번호가 맞아도 로그인할 수 없다. (맞는지 틀린지 알려주지 않는다.)</li>
 * </ul>
 */
@Slf4j
@Service
public class LoginService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final LoginLockProperties lockProperties;
    private final Clock clock;

    /** 계정이 없을 때 비밀번호 검증 시간을 맞추려고 쓰는 해시. 누구의 비밀번호도 아니다. */
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder passwordEncoder, AuthTokenService authTokenService,
                        LoginLockProperties lockProperties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.lockProperties = lockProperties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * 로그인에 성공하면 토큰을 발급한다. 실패 횟수 기록은 오류를 던져도 되돌리지 않는다.
     * (되돌리면 실패 횟수가 늘지 않아서 잠금이 걸리지 않는다.)
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenPair login(LoginRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        User user = users.findByEmailForUpdate(SignupService.normalizeEmail(request.loginId())).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordLoginFailure(now, lockProperties.maxFailures(), lockProperties.lockDuration());
            if (user.isLocked(now)) {
                log.warn("로그인 실패가 {}회 쌓여 사용자 {}를 {} 동안 잠급니다.",
                    lockProperties.maxFailures(), user.getId(), lockProperties.lockDuration());
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.resetLoginFailures();
        return authTokenService.issue(user.getId());
    }

}
