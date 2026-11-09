package com.richreach.user.service;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 사용자의 요청이 겹치지 않도록 사용자 행을 잠근다. (버튼 중복 클릭 등으로 같은 작업이 두 번 시작되는 것을 막는다.)
 * 다른 도메인이 user 도메인의 엔티티나 리포지토리를 직접 읽지 않고 이 서비스를 거치게 하려는 것이다.
 *
 * <p>잠금은 호출한 쪽의 트랜잭션이 끝날 때까지만 유지되므로 반드시 트랜잭션 안에서 불러야 하고, 트랜잭션이 없으면 오류다.
 * ({@link Propagation#MANDATORY}) 호출한 쪽의 트랜잭션에 참여하므로 잠금도 그 트랜잭션이 끝날 때 풀린다.
 */
@Service
@RequiredArgsConstructor
public class UserLockService {

    private final UserRepository users;

    /** 사용자 행을 잠근다. 토큰은 유효하지만 사용자가 이미 삭제된 경우에는 {@code AUTH-005}다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(long userId) {
        users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
    }

}
