-- connected_id를 AES-GCM으로 암호화해서 저장하도록 변경한다.
-- 매번 랜덤 IV를 써서 암호화하므로 같은 평문도 저장할 때마다 암호문이 달라진다.
-- 그래서 DB UNIQUE 제약으로는 더 이상 중복을 막을 수 없어 제거한다.
-- (사용자당 연동 1개라는 규칙은 user_id UNIQUE로 계속 보장된다.)
ALTER TABLE codef_connections
    DROP INDEX uk_codef_connections_connected_id,
    MODIFY COLUMN connected_id VARCHAR(255) NOT NULL
        COMMENT 'CODEF가 발급한 connectedId. AES-GCM으로 암호화해서 저장(Base64)';
