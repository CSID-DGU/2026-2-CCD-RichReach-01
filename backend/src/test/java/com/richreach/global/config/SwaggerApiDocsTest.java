package com.richreach.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.TestcontainersConfiguration;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 실제로 만들어지는 API 문서(/v3/api-docs)에 토큰 입력 버튼 설정이 반영되는지 확인한다. 문서는 로그인 없이 열린다. */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerApiDocsTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("API 문서에 Bearer(JWT) 인증 방식과 기본 보안 요구가 들어 있다")
    void apiDocsContainBearerScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
            .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @Test
    @DisplayName("로그인·가입 같은 /api/auth/ API는 토큰이 필요 없다고 표시되고, 로그인이 필요한 API는 기본 요구를 따른다")
    void publicAndProtectedOperationsAreMarkedDifferently() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            // 토큰이 필요 없는 API: 보안 요구가 빈 배열이다.
            .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
            .andExpect(jsonPath("$.paths['/api/auth/signup'].post.security").isEmpty())
            .andExpect(jsonPath("$.paths['/api/auth/password/code'].post.security").isEmpty())
            // 로그인이 필요한 API: 개별 설정이 없어서 문서 전체의 기본 보안 요구를 따른다.
            .andExpect(jsonPath("$.paths['/api/card-companies'].get.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/connections/agreements'].post.security").doesNotExist());
    }

}
