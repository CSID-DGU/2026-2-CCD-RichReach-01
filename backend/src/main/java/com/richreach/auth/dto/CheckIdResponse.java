package com.richreach.auth.dto;

/** @param available 가입에 쓸 수 있는 아이디면 true, 이미 가입된 아이디면 false */
public record CheckIdResponse(boolean available) {
}
