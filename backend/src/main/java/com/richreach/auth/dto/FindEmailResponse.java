package com.richreach.auth.dto;

/** @param maskedEmail 일부를 가린 이메일. 예: {@code ri***@example.com} */
public record FindEmailResponse(String maskedEmail) {
}
