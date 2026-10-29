package com.richreach.auth.service;

import com.richreach.auth.config.PasswordResetProperties;
import com.richreach.auth.dto.PasswordCodeRequest;
import com.richreach.auth.dto.PasswordCodeResponse;
import com.richreach.auth.dto.PasswordResetRequest;
import com.richreach.auth.dto.PasswordVerifyRequest;
import com.richreach.auth.dto.PasswordVerifyResponse;
import com.richreach.auth.entity.PasswordResetCode;
import com.richreach.auth.repository.PasswordResetCodeRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.security.JwtTokenProvider;
import com.richreach.global.security.JwtTokenProvider.IssuedToken;
import com.richreach.global.security.JwtTokenProvider.TokenClaims;
import com.richreach.global.security.TokenType;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정: 인증번호 발송 → 인증번호 확인(재설정 토큰 발급) → 새 비밀번호 설정.
 *
 * <ul>
 *   <li>인증번호는 6자리이고 BCrypt 해시로만 저장한다. 유효 시간, 재전송 대기 시간, 틀릴 수 있는 횟수는 설정으로 정한다.</li>
 *   <li>가입되지 않은 이메일로 발송을 요청해도 같은 모양의 성공 응답을 준다. 이 경우 인증번호는 만들지 않으므로
 *       확인 단계에서 "인증번호가 올바르지 않음"이 된다. (가입 여부를 완전히 숨기지는 못한다. 가입된 이메일은
 *       재전송 대기 시간 안에 다시 요청하면 429이고 응답도 조금 늦다. 이메일 찾기와 아이디 중복 확인이 이미
 *       가입 여부를 알려주는 API라서 거기까지는 막지 않았다.)</li>
 *   <li>재설정 토큰은 현재 비밀번호 해시의 지문을 묶어서 만든다. 비밀번호가 바뀌면 지문이 달라져서 같은 토큰을 다시 쓸 수 없다.</li>
 *   <li>비밀번호를 바꾸면 기존 로그인(refresh 토큰)을 모두 끊고 로그인 실패 잠금을 푼다.</li>
 * </ul>
 */
@Slf4j
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeSender sender;
    private final JwtTokenProvider tokenProvider;
    private final AuthTokenService authTokenService;
    private final PasswordResetProperties properties;
    private final Clock clock;

    public PasswordResetService(UserRepository users, PasswordResetCodeRepository codes,
                                PasswordEncoder passwordEncoder, VerificationCodeSender sender,
                                JwtTokenProvider tokenProvider, AuthTokenService authTokenService,
                                PasswordResetProperties properties, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.passwordEncoder = passwordEncoder;
        this.sender = sender;
        this.tokenProvider = tokenProvider;
        this.authTokenService = authTokenService;
        this.properties = properties;
        this.clock = clock;
    }

    /** 인증번호를 만들어 발송한다. 재전송 대기 시간 안에 다시 요청하면 {@link ErrorCode#TOO_MANY_REQUESTS}다. */
    @Transactional
    public PasswordCodeResponse sendCode(PasswordCodeRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        int expiresIn = (int) properties.codeTtl().toSeconds();
        int resendIn = (int) properties.resendInterval().toSeconds();

        User user = users.findByEmail(SignupService.normalizeEmail(request.loginId())).orElse(null);
        if (user == null) {
            return new PasswordCodeResponse(expiresIn, resendIn, null);
        }

        PasswordResetCode existing = codes.findByUserIdForUpdate(user.getId()).orElse(null);
        if (existing != null && !existing.canResend(now, properties.resendInterval())) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);
        try {
            if (existing == null) {
                codes.saveAndFlush(new PasswordResetCode(user.getId(), codeHash, now, properties.codeTtl()));
            } else {
                existing.renew(codeHash, now, properties.codeTtl());
            }
        } catch (DataIntegrityViolationException e) {
            // 같은 사용자가 동시에 처음 요청해서 다른 요청이 먼저 저장한 경우
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        sender.send(user.getEmail(), code);
        return new PasswordCodeResponse(expiresIn, resendIn, properties.exposeCode() ? code : null);
    }

    /**
     * 인증번호를 확인하고 재설정 토큰을 발급한다. 틀린 횟수는 오류를 던져도 남겨야 해서 되돌리지 않는다.
     * 맞으면 인증번호를 지워서 다시 쓸 수 없게 하고, 최대 횟수만큼 틀리면 그 인증번호를 폐기한다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public PasswordVerifyResponse verifyCode(PasswordVerifyRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        User user = users.findByEmail(SignupService.normalizeEmail(request.loginId()))
            .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID));
        PasswordResetCode stored = codes.findByUserIdForUpdate(user.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID));

        if (stored.isExpired(now)) {
            codes.delete(stored);
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if (!passwordEncoder.matches(request.code(), stored.getCodeHash())) {
            if (stored.recordFailure() >= properties.maxAttempts()) {
                log.warn("인증번호를 {}번 틀려 사용자 {}의 인증번호를 폐기합니다.", properties.maxAttempts(), user.getId());
                codes.delete(stored);
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
        }

        codes.delete(stored);
        IssuedToken token = tokenProvider.createPasswordResetToken(
            user.getId(), fingerprint(user.getPasswordHash()), properties.resetTokenTtl());
        return new PasswordVerifyResponse(token.value());
    }

    /**
     * 재설정 토큰으로 새 비밀번호를 설정한다. 성공하면 기존 로그인을 모두 끊고 로그인 실패 잠금을 푼다.
     * 비밀번호가 바뀌면 토큰에 묶인 지문이 달라져서 같은 토큰으로 두 번 바꿀 수 없다.
     */
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        TokenClaims claims = tokenProvider.parse(request.resetToken(), TokenType.PASSWORD_RESET);
        User user = users.findByIdForUpdate(claims.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (!fingerprint(user.getPasswordHash()).equals(claims.binding())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        user.resetLoginFailures();
        authTokenService.revokeAll(user.getId());
    }

    /** 비밀번호 해시 자체를 토큰에 넣지 않으려고 SHA-256으로 줄인 값을 묶는다. */
    private static String fingerprint(String passwordHash) {
        return AuthTokenService.hash(passwordHash).substring(0, 32);
    }

}
