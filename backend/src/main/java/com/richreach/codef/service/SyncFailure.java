package com.richreach.codef.service;

import com.richreach.codef.entity.CodefSyncJob;

/**
 * 동기화 실패를 화면에 보여줄 코드와 문구로 바꾼다. 문구는 사용자에게 그대로 보여줘도 된다.
 * CODEF 응답 내용은 담지 않는다. 실패 종류를 더 나누려면 확인한 CODEF 코드를 여기에 추가한다.
 *
 * @param code   화면 처리용 코드
 * @param reason 사용자에게 보여줄 문구
 */
record SyncFailure(String code, String reason) {

    /** 작업 기록(result_code)에 남기는 값. CODEF 코드(CF-xxxxx)와 구분되게 CF로 시작하지 않는다. */
    static final String RESULT_TIMEOUT = "SYNC-TIMEOUT";
    static final String RESULT_UNAVAILABLE = "CODEF-UNAVAILABLE";
    static final String RESULT_ERROR = "SYNC-ERROR";

    static SyncFailure of(CodefSyncJob job, boolean timedOut) {
        if (timedOut) {
            return of(RESULT_TIMEOUT);
        }
        return of(job.getResultCode());
    }

    static SyncFailure of(String resultCode) {
        if (RESULT_TIMEOUT.equals(resultCode)) {
            return new SyncFailure("SYNC_TIMEOUT", "카드 정보를 불러오는 데 시간이 너무 오래 걸렸어요. 다시 시도해 주세요.");
        }
        if (RESULT_UNAVAILABLE.equals(resultCode) || CodefFailures.SERVER_ERROR.equals(resultCode)) {
            return new SyncFailure("CODEF_UNAVAILABLE", "카드사 연동 서버에 문제가 있어요. 잠시 후 다시 시도해 주세요.");
        }
        if (CodefFailures.ACCOUNT_RESTRICTED.equals(resultCode)) {
            return new SyncFailure("ACCOUNT_RESTRICTED",
                "반복 로그인으로 카드사 계정이 일시 제한되었어요. 최대 1시간 뒤에 다시 시도해 주세요.");
        }
        return new SyncFailure("SYNC_FAILED", "카드 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
    }

}
