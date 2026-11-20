-- 카드 정보와 연결 관리(SETTINGS-03). (이슈 #275)
--
--  * login_id_masked: 화면에 "연결 계정"으로 보여줄 가린 카드사 아이디. 연결(재연결)할 때 앞 2자만 남기고 가려서 저장한다.
--    카드사 아이디 원문과 비밀번호는 지금도 저장하지 않는다. 이 컬럼이 생기기 전에 연결한 계정은 NULL이고, 다시 연결하면 채워진다.
--  * auto_sync_enabled: 거래내역 자동 동기화를 쓸지 사용자가 정한 설정. 이 컬럼은 설정 값만 저장하고, 실제 주기 실행은 별도다.
--    기본값은 켬이다. (카드 연동 때 거래내역 조회에 동의했고, 거래가 들어올 때마다 플랜을 다시 계산하는 것이 서비스의 기본 흐름이다)
--  * 카드사 연결 해제는 계정 행을 지우지 않고 status를 DELETED로 바꾼다. (동기화 작업 기록이 계정을 참조한다)

ALTER TABLE codef_org_accounts
    ADD COLUMN login_id_masked   VARCHAR(30) NULL COMMENT '가린 카드사 아이디(앞 2자만 남김). 연결할 때 저장하고, 기록이 없으면 NULL',
    ADD COLUMN auto_sync_enabled BOOLEAN     NOT NULL DEFAULT TRUE COMMENT '거래내역 자동 동기화 설정. 설정 값만 저장한다';
