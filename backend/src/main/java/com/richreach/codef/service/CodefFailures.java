package com.richreach.codef.service;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;

/**
 * CODEF의 실패를 사용자에게 보여줄 오류로 바꾼다. 카드사 연결 생성, 재시도, 동기화 실패 안내가 같은 기준을 쓰도록 한 곳에 둔다.
 * 추가 인증(CF-03002)과 계정 잠김처럼 따로 안내해야 하는 코드는 샌드박스 응답을 확인한 뒤 나눈다.
 */
final class CodefFailures {

    /** CODEF 서버 쪽 일시 오류. 사용자의 입력과 상관없으므로 인증 실패가 아니라 연동 서버 오류로 알린다. */
    static final String SERVER_ERROR = "CF-09999";

    private CodefFailures() {
    }

    /** 계정 등록·갱신 결과가 실패일 때의 오류. CODEF 서버 오류만 연동 서버 오류로 보고, 나머지는 인증 실패로 본다. */
    static BusinessException authFailure(String codefCode) {
        if (SERVER_ERROR.equals(codefCode)) {
            return new BusinessException(ErrorCode.CODEF_UNAVAILABLE);
        }
        return new BusinessException(ErrorCode.CARD_COMPANY_AUTH_FAILED);
    }

    /** CODEF를 호출하지 못한 오류(외부 API 오류)는 연동 서버 오류로 바꾸고, 그 밖의 오류는 그대로 둔다. */
    static BusinessException translate(BusinessException e) {
        if (e.getErrorCode() == ErrorCode.EXTERNAL_API_ERROR) {
            return new BusinessException(ErrorCode.CODEF_UNAVAILABLE);
        }
        return e;
    }

}
