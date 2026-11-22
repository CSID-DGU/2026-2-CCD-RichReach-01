package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.TestcontainersConfiguration;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code codef.auto-sync.enabled=true}로 켜면 자동 동기화 스케줄러가 만들어진다. (꺼진 기본값은 DevProfileBootTest가 본다) */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "codef.auto-sync.enabled=true")
class CodefAutoSyncEnabledBootTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("켜면 자동 동기화 스케줄러가 만들어진다")
    void schedulerExistsWhenEnabled() {
        assertThat(context.containsBean("codefAutoSyncScheduler")).isTrue();
    }

}
