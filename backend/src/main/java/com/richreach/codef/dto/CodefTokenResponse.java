package com.richreach.codef.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** CODEF 토큰 발급 API의 응답 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CodefTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("scope") String scope
) {

    /** 토큰이 로그에 출력되지 않도록 값을 숨긴다. */
    @Override
    public String toString() {
        return "CodefTokenResponse[accessToken=****, tokenType=" + tokenType
            + ", expiresIn=" + expiresIn + ", scope=" + scope + "]";
    }

}
