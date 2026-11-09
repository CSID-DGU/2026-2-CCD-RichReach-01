package com.richreach.codef.service;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.util.Set;

/**
 * CODEF의 실패를 사용자에게 보여줄 오류로 바꾼다. 카드사 연결 생성, 재시도, 동기화 실패 안내가 같은 기준을 쓰도록 한 곳에 둔다.
 * 추가 인증(CF-03002)과 계정 잠김처럼 따로 안내해야 하는 코드는 CODEF 에러코드 표나 데모 환경 응답으로 확인한 뒤 나눈다.
 *
 * <p>확인한 코드:
 * <ul>
 *   <li>{@code CF-12803}: 아이디 또는 비밀번호 오류. 사용자가 고칠 수 있는 입력 오류라서 인증 실패({@code CONN-003})다.
 *       아이디 오류와 비밀번호 오류를 구분하지 못할 때 나온다.</li>
 *   <li>{@code CF-04020}: 비밀번호 복호화 실패. 사용자의 입력이 아니라 CODEF 공개키 설정 같은 우리 쪽 문제라서
 *       "입력한 정보를 확인해 달라"고 안내하지 않고 연동 서버 오류({@code CONN-005})로 알린다.</li>
 *   <li>{@code CF-09999}: CODEF 서버 쪽 일시 오류. 연동 서버 오류({@code CONN-005})다.</li>
 *   <li>{@code CF-12207}: 반복 로그인으로 카드사 계정이 일시 제한됨(최대 1시간). 다시 로그인하면 제한이 길어질 수 있어서
 *       연결과 재시도를 막는({@code CONN-008}) 오류이고, 동기화 실패는 다시 시도하면 안 되는 오류({@code retryable=false})다.</li>
 *   <li>{@code CF-04034}: 추가 인증이 필요한 카드사 계정. 추가 인증 절차는 아직 지원하지 않아서 {@code CONN-009}로 알린다.</li>
 * </ul>
 * 위에 없는 코드는 인증 실패로 본다.
 */
final class CodefFailures {

    /** CODEF 서버 쪽 일시 오류. 사용자의 입력과 상관없으므로 인증 실패가 아니라 연동 서버 오류로 알린다. */
    static final String SERVER_ERROR = "CF-09999";

    /** 비밀번호 복호화 실패. 사용자가 고칠 수 없는 설정 문제라서 연동 서버 오류로 알린다. */
    static final String PASSWORD_DECRYPT_ERROR = "CF-04020";

    /** 반복 로그인으로 카드사 계정이 일시 제한됨(최대 1시간). 이때 다시 로그인하면 제한이 길어질 수 있다. */
    static final String ACCOUNT_RESTRICTED = "CF-12207";

    /** 추가 인증이 필요한 카드사 계정. 추가 인증 절차는 아직 지원하지 않는다. */
    static final String ADDITIONAL_AUTH_REQUIRED = "CF-04034";

    private static final Set<String> SERVICE_ERRORS = Set.of(SERVER_ERROR, PASSWORD_DECRYPT_ERROR);

    private CodefFailures() {
    }

    /**
     * 계정 등록·갱신 결과가 실패일 때의 오류. 계정이 일시 제한됐으면 다시 시도하지 않도록 막고, 추가 인증이 필요하면 아직
     * 지원하지 않는다고 알린다. 사용자가 고칠 수 없는 오류({@link #SERVICE_ERRORS})는 연동 서버 오류로 보고, 나머지는 인증
     * 실패로 본다.
     */
    static BusinessException authFailure(String codefCode) {
        if (ACCOUNT_RESTRICTED.equals(codefCode)) {
            return new BusinessException(ErrorCode.CONNECTION_RETRY_BLOCKED);
        }
        if (ADDITIONAL_AUTH_REQUIRED.equals(codefCode)) {
            return new BusinessException(ErrorCode.CARD_COMPANY_ADDITIONAL_AUTH_UNSUPPORTED);
        }
        if (SERVICE_ERRORS.contains(codefCode)) {
            return new BusinessException(ErrorCode.CODEF_UNAVAILABLE);
        }
        return new BusinessException(ErrorCode.CARD_COMPANY_AUTH_FAILED);
    }

    /** 이 실패 뒤에 다시 시도해도 되는지. 계정이 일시 제한된 경우는 다시 로그인하면 제한이 길어질 수 있어서 안 된다. */
    static boolean isRetryable(String codefCode) {
        return !ACCOUNT_RESTRICTED.equals(codefCode);
    }

    /** CODEF를 호출하지 못한 오류(외부 API 오류)는 연동 서버 오류로 바꾸고, 그 밖의 오류는 그대로 둔다. */
    static BusinessException translate(BusinessException e) {
        if (e.getErrorCode() == ErrorCode.EXTERNAL_API_ERROR) {
            return new BusinessException(ErrorCode.CODEF_UNAVAILABLE);
        }
        return e;
    }

}
