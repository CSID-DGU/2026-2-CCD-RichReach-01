package com.richreach.codef.client;

import com.richreach.codef.config.CodefProperties;
import com.richreach.codef.dto.CodefTokenResponse;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * CODEF 토큰 발급 API(OAuth2 client_credentials) 호출.
 * 요청마다 새 토큰을 발급하므로, 일반적으로는 {@link CodefTokenProvider}를 통해 캐시된 토큰을 사용한다.
 * 시크릿과 토큰은 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class CodefTokenClient {

    private static final String TOKEN_PATH = "/oauth/token";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RestClient restClient;
    private final CodefProperties properties;

    public CodefTokenClient(@Qualifier("codefOauthRestClient") RestClient codefOauthRestClient,
        CodefProperties properties) {
        this.restClient = codefOauthRestClient;
        this.properties = properties;
    }

    public CodefTokenResponse issueToken() {
        if (!properties.hasCredentials()) {
            // 설정 안내는 서버 로그에만 남긴다. 응답에는 내부 설정 이름을 내보내지 않는다.
            log.error("CODEF 클라이언트 정보가 설정되지 않았습니다. CODEF_CLIENT_ID, CODEF_CLIENT_SECRET을 확인하세요.");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "read");

        String body;
        try {
            body = restClient.post()
                .uri(TOKEN_PATH)
                .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("CODEF 토큰 발급 실패: HTTP {}. 클라이언트 ID/시크릿과 서비스 승인 상태를 확인하세요.", status);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        } catch (RestClientException e) {
            log.warn("CODEF 토큰 발급 요청 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "CODEF 서버에 연결하지 못했습니다.");
        }

        return parse(body);
    }

    /** 응답이 JSON이면 그대로, URL 인코딩되어 있으면 디코딩한 뒤 파싱한다. */
    private CodefTokenResponse parse(String body) {
        if (body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "CODEF 토큰 응답이 비어 있습니다.");
        }
        String json = body.strip();

        CodefTokenResponse response;
        try {
            if (!json.startsWith("{")) {
                json = URLDecoder.decode(json, StandardCharsets.UTF_8);
            }
            response = JSON_MAPPER.readValue(json, CodefTokenResponse.class);
        } catch (IllegalArgumentException | JacksonException e) {
            // 잘못된 퍼센트 인코딩(IllegalArgumentException)도 외부 서비스 문제이므로 같은 오류로 처리한다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "CODEF 토큰 응답 형식이 올바르지 않습니다.");
        }
        if (response.accessToken() == null || response.accessToken().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "CODEF 토큰 응답에 토큰이 없습니다.");
        }
        return response;
    }

}
