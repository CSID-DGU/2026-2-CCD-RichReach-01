package com.richreach.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 설정. 로그인이 필요한 API를 시험할 수 있도록 오른쪽 위에 토큰 입력 버튼(Authorize)을 둔다.
 * 사용법: 로그인 API(POST /api/auth/login)로 받은 accessToken을 Authorize에 넣는다. ('Bearer '는 붙이지 않는다.)
 */
@Configuration
public class SwaggerConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    /** 로그인 없이 쓰는 API의 경로. 이 아래는 자물쇠 표시와 토큰 전송에서 제외한다. (SecurityConfig의 공개 경로와 같다.) */
    private static final String PUBLIC_API_PREFIX = "/api/auth/";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("RichReach API")
                .description("RichReach 백엔드 API 명세")
                .version("v1"))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("로그인(POST /api/auth/login)으로 받은 accessToken을 입력한다. 'Bearer '는 붙이지 않는다. "
                    + "만료되면(AUTH-004) 재발급(POST /api/auth/reissue)으로 새 토큰을 받는다.")))
            // 기본은 모든 API가 로그인을 요구한다. SecurityConfig가 anyRequest().authenticated()인 것과 같다.
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /** 가입·로그인처럼 토큰이 필요 없는 API는 기본 보안 요구를 비워서 자물쇠가 나오지 않게 한다. */
    @Bean
    public OpenApiCustomizer publicApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (path.startsWith(PUBLIC_API_PREFIX)) {
                    item.readOperations().forEach(operation -> operation.setSecurity(List.of()));
                }
            });
        };
    }

}
