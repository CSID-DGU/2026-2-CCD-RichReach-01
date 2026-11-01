package com.richreach.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwaggerConfigTest {

    private final SwaggerConfig config = new SwaggerConfig();

    @Test
    @DisplayName("토큰 입력 버튼용 Bearer(JWT) 인증 방식이 정의되어 있다")
    void definesBearerScheme() {
        OpenAPI openApi = config.openAPI();

        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(scheme.getDescription()).contains("accessToken").contains("Bearer ");
    }

    @Test
    @DisplayName("기본으로 모든 API가 Bearer 토큰을 요구한다")
    void requiresBearerByDefault() {
        OpenAPI openApi = config.openAPI();

        assertThat(openApi.getSecurity()).hasSize(1);
        assertThat(openApi.getSecurity().get(0)).containsKey("bearerAuth");
    }

    @Test
    @DisplayName("/api/auth/ 아래 API는 토큰이 필요 없어서 보안 요구가 비워지고, 나머지는 그대로 둔다")
    void publicAuthPathsAreExcluded() {
        Operation login = new Operation();
        Operation reissue = new Operation();
        Operation cards = new Operation();
        OpenAPI openApi = new OpenAPI().paths(new Paths()
            .addPathItem("/api/auth/login", new PathItem().post(login))
            .addPathItem("/api/auth/reissue", new PathItem().post(reissue))
            .addPathItem("/api/card-companies", new PathItem().get(cards)));

        config.publicApiCustomizer().customise(openApi);

        assertThat(login.getSecurity()).isEqualTo(List.of());
        assertThat(reissue.getSecurity()).isEqualTo(List.of());
        assertThat(cards.getSecurity()).isNull();
    }

    @Test
    @DisplayName("API가 하나도 없는 문서에서도 오류 없이 동작한다")
    void handlesEmptyPaths() {
        config.publicApiCustomizer().customise(new OpenAPI());
        config.publicApiCustomizer().customise(new OpenAPI().paths(new Paths()));
    }

}
