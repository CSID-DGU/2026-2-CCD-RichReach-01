package com.richreach.codef.service;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 같은 사용자의 카드 연동 요청이 겹치지 않도록 사용자 행을 잠근다. (버튼 중복 클릭 등으로 connectedId가 두 번 만들어지거나
 * 동기화가 겹쳐 시작되는 것을 막는다.)
 *
 * <p>user 도메인의 Repository를 쓰는 곳을 이 클래스 하나로 모아, 카드 연동 서비스들이 다른 도메인의 Repository를 직접 알지
 * 않게 한다. 잠금은 호출한 쪽의 트랜잭션이 끝날 때까지만 유지되므로 반드시 트랜잭션 안에서 불러야 한다.
 */
@Component
@RequiredArgsConstructor
class ConnectionUserLock {

    private final UserRepository users;

    /** 사용자 행을 잠그고 돌려준다. 토큰은 유효하지만 사용자가 이미 삭제된 경우에는 {@code AUTH-005}다. */
    User lock(long userId) {
        return users.findByIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
    }

}
