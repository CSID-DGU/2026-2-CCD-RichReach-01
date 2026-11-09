-- 로그인 실패 잠금: 연속으로 실패한 횟수와, 잠겨 있는 동안의 해제 시각을 사용자에 둔다.
-- 서버를 다시 켜도 잠금이 유지되도록 메모리가 아니라 DB에 저장한다.
ALTER TABLE users
    ADD COLUMN failed_login_count INT      NOT NULL DEFAULT 0 COMMENT '연속 로그인 실패 횟수. 로그인에 성공하면 0으로 되돌린다',
    ADD COLUMN locked_until       DATETIME NULL COMMENT 'NULL이 아니고 현재보다 이후면 잠긴 상태. 이 시각까지 로그인할 수 없다';
