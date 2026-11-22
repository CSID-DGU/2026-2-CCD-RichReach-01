package com.richreach.codef.dto;

/**
 * 자동 동기화 대상 카드사 계정 하나. 계정과 그 계정의 주인(사용자)만 담는다. 아이디, 카드번호, connectedId 같은 값은
 * 읽지 않는다.
 *
 * @param accountId 카드사 연결 ID({@code codef_org_accounts.id})
 * @param userId    그 연결의 주인
 */
public record AutoSyncCandidate(Long accountId, Long userId) {

}
