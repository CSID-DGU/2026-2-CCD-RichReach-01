package com.richreach.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.auth.config.PasswordResetProperties;
import com.richreach.auth.dto.PasswordCodeRequest;
import com.richreach.auth.dto.PasswordCodeResponse;
import com.richreach.auth.entity.PasswordResetCode;
import com.richreach.auth.repository.PasswordResetCodeRepository;
import com.richreach.global.security.JwtTokenProvider;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 인증번호를 응답에 담는 설정(expose-code)이 꺼져 있으면 인증번호가 응답에 나오지 않는지 확인한다. */
class PasswordResetExposeCodeTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetCodeRepository codes = mock(PasswordResetCodeRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final VerificationCodeSender sender = mock(VerificationCodeSender.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("UTC"));

    private PasswordResetService service(boolean exposeCode) {
        PasswordResetProperties properties = new PasswordResetProperties(
            Duration.ofMinutes(3), Duration.ofSeconds(60), 5, Duration.ofMinutes(10), exposeCode);
        return new PasswordResetService(users, codes, passwordEncoder, sender, mock(JwtTokenProvider.class),
            mock(AuthTokenService.class), properties, clock);
    }

    private void givenExistingUser() {
        User user = new User("rich@example.com", "hash", "김리치", "01012345678", "REAL", LocalDateTime.now());
        when(users.findByEmail("rich@example.com")).thenReturn(Optional.of(user));
        when(codes.findByUserIdForUpdate(any())).thenReturn(Optional.empty());
        when(codes.saveAndFlush(any(PasswordResetCode.class))).thenAnswer(call -> call.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-hash");
    }

    @Test
    @DisplayName("expose-code가 꺼져 있으면 인증번호는 발송기로만 보내고 응답에는 담지 않는다")
    void codeIsNotInTheResponseWhenDisabled() {
        givenExistingUser();

        PasswordCodeResponse response = service(false).sendCode(new PasswordCodeRequest("rich@example.com"));

        assertThat(response.devCode()).isNull();
        assertThat(response.expiresIn()).isEqualTo(180);
        assertThat(response.resendIn()).isEqualTo(60);
        verify(sender).send(any(), anyString());
    }

    @Test
    @DisplayName("expose-code가 켜져 있으면 6자리 인증번호가 응답에도 담긴다")
    void codeIsInTheResponseWhenEnabled() {
        givenExistingUser();

        PasswordCodeResponse response = service(true).sendCode(new PasswordCodeRequest("rich@example.com"));

        assertThat(response.devCode()).matches("\\d{6}");
    }

    @Test
    @DisplayName("PasswordResetProperties에 값을 주지 않으면 expose-code는 꺼져 있다")
    void defaultsToDisabled() {
        PasswordResetProperties defaults = new PasswordResetProperties(null, null, 0, null, false);

        assertThat(defaults.exposeCode()).isFalse();
    }

}
