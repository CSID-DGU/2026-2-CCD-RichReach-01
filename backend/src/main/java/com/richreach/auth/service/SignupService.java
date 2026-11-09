package com.richreach.auth.service;

import com.richreach.auth.config.TermsProperties;
import com.richreach.auth.dto.SignupRequest;
import com.richreach.auth.dto.TokenPair;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.user.entity.TermType;
import com.richreach.user.entity.User;
import com.richreach.user.entity.UserTermAgreement;
import com.richreach.user.repository.UserRepository;
import com.richreach.user.repository.UserTermAgreementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 아이디(이메일) 중복 확인과 회원가입. */
@Service
public class SignupService {

    /** 가입하는 사용자는 모두 실사용자다. (PERSONA는 평가용 합성 사용자) */
    private static final String USER_TYPE_REAL = "REAL";

    private final UserRepository users;
    private final UserTermAgreementRepository agreements;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final TermsProperties terms;
    private final Clock clock;

    public SignupService(UserRepository users, UserTermAgreementRepository agreements,
                         PasswordEncoder passwordEncoder, AuthTokenService authTokenService,
                         TermsProperties terms, Clock clock) {
        this.users = users;
        this.agreements = agreements;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.terms = terms;
        this.clock = clock;
    }

    /** 가입에 쓸 수 있는 아이디인지. */
    @Transactional(readOnly = true)
    public boolean isLoginIdAvailable(String loginId) {
        return !users.existsByEmail(normalizeEmail(loginId));
    }

    /**
     * 계정을 만들고 약관 동의 이력을 남긴 뒤, 바로 로그인한 상태가 되도록 토큰을 발급한다.
     * 중간에 하나라도 실패하면 계정도 만들어지지 않는다.
     */
    @Transactional
    public TokenPair signup(SignupRequest request) {
        if (!request.termsAgreed()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
        }
        String email = normalizeEmail(request.loginId());
        String phone = normalizePhone(request.phoneNumber());
        if (users.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (users.existsByPhone(phone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        User user;
        try {
            // 위의 중복 확인은 동시에 같은 값으로 가입하는 요청을 막지 못한다. 최종 방어는 DB의 유일 제약이다.
            user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password()),
                request.name().trim(), phone, USER_TYPE_REAL, now));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(duplicateErrorOf(e));
        }
        agreements.save(new UserTermAgreement(user.getId(), TermType.SERVICE_AND_PRIVACY, terms.signupVersion(), now));
        return authTokenService.issue(user.getId());
    }

    /** 이메일은 앞뒤 공백을 없애고 소문자로 저장한다. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 전화번호는 하이픈과 공백을 없애고 숫자만 저장한다. 같은 번호가 표기만 달라서 중복 확인을 빠져나가는 것을 막는다. */
    static String normalizePhone(String phone) {
        return phone.replaceAll("[-\\s]", "");
    }

    /** 유일 제약 위반이 이메일 때문인지 전화번호 때문인지. 제약 이름({@code uk_users_phone})으로 구분한다. */
    public static ErrorCode duplicateErrorOf(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains("uk_users_phone")) {
                return ErrorCode.DUPLICATE_PHONE;
            }
        }
        return ErrorCode.DUPLICATE_EMAIL;
    }

}
