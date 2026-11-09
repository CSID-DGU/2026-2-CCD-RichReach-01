package com.richreach.codef.client;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.codef.config.CodefDebugProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * CODEF 상품 API 호출.
 * - 요청 본문: JSON 문자열을 URL 인코딩해서 POST
 * - 인증: Authorization: Bearer {액세스 토큰}. 토큰이 거부되면(401, CF-00401) 새로 발급해서 한 번만 재시도한다.
 * - 응답: JSON (URL 인코딩되어 오면 디코딩)
 * 요청/응답 본문에는 개인정보가 포함될 수 있으므로 로그에 남기지 않는다. (경로와 결과 코드만 기록)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodefApiClient {

    private static final String INVALID_TOKEN_RESULT_CODE = "CF-00401";

    @Qualifier("codefApiRestClient")
    private final RestClient restClient;
    private final CodefTokenProvider tokenProvider;
    private final CodefJsonCodec codec;
    private final CodefDebugProperties debug;

    /**
     * @param path 상품 경로 (예: /v1/kr/card/p/account/card-list)
     * @param body 요청 파라미터 (JSON으로 직렬화된다)
     * @return CODEF 응답 전체 (result.code가 CF-00000이면 성공)
     */
    public JsonNode post(String path, Object body) {
        String encodedBody = codec.encodeBody(body);

        Response response = send(path, encodedBody);
        if (response.status() == 401 || isInvalidTokenResult(response.json())) {
            log.info("CODEF 토큰이 거부되어 새로 발급 후 재시도합니다. path={}", path);
            tokenProvider.invalidate(response.token());
            response = send(path, encodedBody);
        }

        if (response.status() < 200 || response.status() >= 300) {
            log.warn("CODEF API 호출 실패: path={}, HTTP {}", path, response.status());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }

        log.info("CODEF API 호출: path={}, resultCode={}", path,
            response.json().path("result").path("code").asString(""));
        if (debug.logShape()) {
            // 응답 구조를 확인할 때만 켠다. 값은 남기지 않고, 필드 경로와 타입(결과 코드만 값)을 남긴다.
            log.info("CODEF 응답 구조: path={}\n{}", path, CodefResponseShape.describe(response.json()));
        }
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
        JsonNode json = entity.getStatusCode().is2xxSuccessful() ? codec.readTree(entity.getBody()) : null;
        return new Response(status, json, token);
    }

    private boolean isInvalidTokenResult(JsonNode json) {
        return json != null
            && INVALID_TOKEN_RESULT_CODE.equals(json.path("result").path("code").asString(""));
    }

    /** @param token 이 요청에 사용한 액세스 토큰. 거부되었을 때 그 토큰만 캐시에서 지우기 위해 들고 다닌다. */
    private record Response(int status, JsonNode json, String token) {

        @Override
        public String toString() {
            return "Response[status=" + status + "]";
        }

    }

}
