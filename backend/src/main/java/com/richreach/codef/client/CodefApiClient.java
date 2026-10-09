package com.richreach.codef.client;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CODEF 상품 API 호출.
 * - 요청 본문: JSON 문자열을 URL 인코딩해서 POST
 * - 인증: Authorization: Bearer {액세스 토큰}. 토큰이 거부되면(401, CF-00401) 새로 발급해서 한 번만 재시도한다.
 * - 응답: JSON (URL 인코딩되어 오면 디코딩)
 * 요청/응답 본문에는 개인정보가 포함될 수 있으므로 로그에 남기지 않는다. (경로와 결과 코드만 기록)
 */
@Slf4j
@Component
public class CodefApiClient {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String INVALID_TOKEN_RESULT_CODE = "CF-00401";

    private final RestClient restClient;
    private final CodefTokenProvider tokenProvider;

    public CodefApiClient(@Qualifier("codefApiRestClient") RestClient codefApiRestClient,
        CodefTokenProvider tokenProvider) {
        this.restClient = codefApiRestClient;
        this.tokenProvider = tokenProvider;
    }

    /**
     * @param path 상품 경로 (예: /v1/kr/card/p/account/card-list)
     * @param body 요청 파라미터 (JSON으로 직렬화된다)
     * @return CODEF 응답 전체 (result.code가 CF-00000이면 성공)
     */
    public JsonNode post(String path, Object body) {
        String encodedBody = encode(body);

        Response response = send(path, encodedBody);
        if (response.status() == 401 || isInvalidTokenResult(response.json())) {
            log.info("CODEF 토큰이 거부되어 새로 발급 후 재시도합니다. path={}", path);
            tokenProvider.invalidate();
            response = send(path, encodedBody);
        }

        if (response.status() < 200 || response.status() >= 300) {
            log.warn("CODEF API 호출 실패: path={}, HTTP {}", path, response.status());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                "CODEF API 호출에 실패했습니다. (HTTP " + response.status() + ")");
        }

        log.info("CODEF API 호출: path={}, resultCode={}", path,
            response.json().path("result").path("code").asString(""));
        return response.json();
    }

    private Response send(String path, String encodedBody) {
        String token = tokenProvider.getAccessToken();
        ResponseEntity<String> entity;
        try {
            entity = restClient.post()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> headers.setBearerAuth(token))
                .body(encodedBody)
                .retrieve()
                // 오류 응답도 상태 코드로 직접 판단한다. (401은 재시도)
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(String.class);
        } catch (RestClientException e) {
            log.warn("CODEF API 요청 실패: path={}, {}", path, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "CODEF 서버에 연결하지 못했습니다.");
        }

        int status = entity.getStatusCode().value();
        JsonNode json = entity.getStatusCode().is2xxSuccessful() ? parse(entity.getBody()) : null;
        return new Response(status, json);
    }

    private String encode(Object body) {
        try {
            return URLEncoder.encode(JSON_MAPPER.writeValueAsString(body), StandardCharsets.UTF_8);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "CODEF 요청 본문을 만들지 못했습니다.");
        }
    }

    /** 응답이 JSON이면 그대로, URL 인코딩되어 있으면 디코딩한 뒤 파싱한다. */
    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "CODEF 응답이 비어 있습니다.");
        }
        String json = body.strip();
        if (!json.startsWith("{") && !json.startsWith("[")) {
            json = URLDecoder.decode(json, StandardCharsets.UTF_8);
        }
        try {
            return JSON_MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "CODEF 응답 형식이 올바르지 않습니다.");
        }
    }

    private boolean isInvalidTokenResult(JsonNode json) {
        return json != null
            && INVALID_TOKEN_RESULT_CODE.equals(json.path("result").path("code").asString(""));
    }

    private record Response(int status, JsonNode json) {

    }

}
