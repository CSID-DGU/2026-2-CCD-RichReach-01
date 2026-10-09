package com.richreach.codef.dto;

/**
 * 토큰 확인용 응답. 토큰 전체 값은 노출하지 않는다.
 *
 * @param fromCache        캐시된 토큰을 재사용했는지 (false면 방금 새로 발급)
 * @param tokenType        토큰 종류 (bearer)
 * @param tokenPreview     마스킹된 토큰 (앞 4글자와 길이만 표시)
 * @param expiresAt        만료 시각
 * @param remainingSeconds 만료까지 남은 초
 */
public record CodefTokenStatusResponse(
    boolean fromCache,
    String tokenType,
    String tokenPreview,
    String expiresAt,
    long remainingSeconds
) {

}
