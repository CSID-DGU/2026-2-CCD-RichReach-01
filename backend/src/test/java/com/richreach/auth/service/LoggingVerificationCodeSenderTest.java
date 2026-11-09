package com.richreach.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.richreach.auth.config.PasswordResetProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** 개발용 발송기가 인증번호를 로그에 남기는 조건을 확인한다. */
class LoggingVerificationCodeSenderTest {

    private static final String CODE = "482913";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingVerificationCodeSender.class);

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private LoggingVerificationCodeSender sender(boolean exposeCode) {
        return new LoggingVerificationCodeSender(new PasswordResetProperties(
            Duration.ofMinutes(3), Duration.ofSeconds(60), 5, Duration.ofMinutes(10), exposeCode));
    }

    private List<String> loggedMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("expose-code가 꺼져 있으면 인증번호를 로그에 남기지 않고 마스킹한 이메일만 남긴다")
    void doesNotLogTheCodeWhenDisabled() {
        sender(false).send("rich@example.com", CODE);

        assertThat(loggedMessages()).hasSize(1);
        assertThat(loggedMessages().get(0)).doesNotContain(CODE).doesNotContain("rich@example.com")
            .contains("ri***@example.com");
    }

    @Test
    @DisplayName("expose-code가 켜져 있으면 개발용으로 인증번호를 로그에 남긴다")
    void logsTheCodeWhenEnabled() {
        sender(true).send("rich@example.com", CODE);

        assertThat(loggedMessages()).hasSize(1);
        assertThat(loggedMessages().get(0)).contains(CODE).contains("ri***@example.com")
            .doesNotContain("rich@example.com");
    }

}
