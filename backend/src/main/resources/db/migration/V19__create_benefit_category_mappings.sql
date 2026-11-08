-- 혜택 업종 코드 → 화면 6개 업종 대응표. 혜택 데이터의 업종 코드(benefit_rules.category, ref_common_standard의 CATEGORY 코드)를
-- 화면에 쓰는 6개 업종(DINING, CAFE, SHOPPING, TRANSPORT, CONVENIENCE_STORE, ETC)에 잇는다.
-- 거래 분류와 혜택 계산이 같은 6개 업종을 쓰게 하는 기준이다. (이슈 #164)
--
-- 대응표는 코드가 아니라 데이터라서 새 마이그레이션으로 행을 바꾸거나 추가하면 된다. 아래는 첫 안이고 팀과 확정하면 바꾼다.
-- ref_common_standard는 데이터 적재 시점이 달라서 FK를 걸지 않는다. (코드표에 없는 새 업종 코드는 매핑하지 않은 코드로 다룬다.)
--
--  * ui_category가 NULL인 행은 업종이 아닌 코드다. 전 가맹점(ALL_MERCHANTS, ALL)이나 결제 수단·해외 같은 조건을 뜻한다.
--  * all_merchants가 TRUE인 행은 업종과 상관없이 모든 가맹점에 적용된다는 뜻이다.
--  * 혜택 하나에 업종이 여러 개면 '|'로 이어진 값이 들어오며, 코드마다 이 표로 바꾼다.
CREATE TABLE benefit_category_mappings (
    category_code  VARCHAR(40)  NOT NULL COMMENT '혜택 업종 코드 (예: CAFE, PUBLIC_TRANSIT)',
    ui_category    VARCHAR(30)  NULL COMMENT '화면용 6개 업종 코드. 업종이 아닌 코드는 NULL',
    all_merchants  BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '업종과 상관없이 모든 가맹점에 적용',
    note           VARCHAR(200) NULL COMMENT '설명',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO benefit_category_mappings (category_code, ui_category, all_merchants, note) VALUES
    -- 음식점
    ('DINING',          'DINING',            FALSE, '음식점'),
    ('DELIVERY_APP',    'DINING',            FALSE, '배달앱'),
    -- 카페
    ('CAFE',            'CAFE',              FALSE, '카페'),
    ('BAKERY',          'CAFE',              FALSE, '베이커리. 카페와 함께 나오는 경우가 많다'),
    -- 편의점
    ('CONVENIENCE',     'CONVENIENCE_STORE', FALSE, '편의점'),
    -- 쇼핑
    ('GROCERY',         'SHOPPING',          FALSE, '마트·식료품'),
    ('ONLINE_SHOPPING', 'SHOPPING',          FALSE, '온라인쇼핑'),
    ('DEPARTMENT',      'SHOPPING',          FALSE, '백화점·아울렛'),
    ('SHOPPING_OTHER',  'SHOPPING',          FALSE, '기타 쇼핑'),
    ('BOOKS',           'SHOPPING',          FALSE, '도서'),
    ('BEAUTY',          'SHOPPING',          FALSE, '뷰티'),
    ('PET',             'SHOPPING',          FALSE, '반려동물'),
    -- 교통
    ('PUBLIC_TRANSIT',  'TRANSPORT',         FALSE, '버스·지하철'),
    ('TAXI',            'TRANSPORT',         FALSE, '택시'),
    ('INTERCITY',       'TRANSPORT',         FALSE, '철도·시외교통'),
    ('FUEL',            'TRANSPORT',         FALSE, '주유'),
    ('EV_CHARGING',     'TRANSPORT',         FALSE, '전기·수소차 충전'),
    ('AUTOMOTIVE',      'TRANSPORT',         FALSE, '자동차'),
    -- 기타. 6개 업종에 자리가 없는 업종이다. 혜택 계산 정확도를 위해 세분화가 필요한지는 팀과 확인한다.
    ('MOVIE',           'ETC',               FALSE, '영화'),
    ('LEISURE',         'ETC',               FALSE, '공연·레저'),
    ('TRAVEL',          'ETC',               FALSE, '여행·숙박·항공'),
    ('EDUCATION',       'ETC',               FALSE, '교육'),
    ('HEALTH',          'ETC',               FALSE, '병원·약국'),
    ('TELECOM',         'ETC',               FALSE, '통신'),
    ('UTILITIES',       'ETC',               FALSE, '공과금·관리비'),
    ('INSURANCE',       'ETC',               FALSE, '보험'),
    ('SUBSCRIPTION',    'ETC',               FALSE, '구독·스트리밍'),
    ('DIGITAL_CONTENT', 'ETC',               FALSE, '디지털 콘텐츠'),
    ('OTHER',           'ETC',               FALSE, '분류 일치 없음 또는 별도 서비스. 가맹점 지정이 있는 혜택은 가맹점 정보로 따로 본다'),
    -- 업종이 아닌 코드
    ('ALL_MERCHANTS',   NULL,                TRUE,  '전 가맹점. 업종과 상관없이 적용'),
    ('ALL',             NULL,                TRUE,  '전 가맹점. 업종과 상관없이 적용 (일부 데이터에서 쓰는 이름)'),
    ('PAYMENT_METHOD',  NULL,                FALSE, '결제 수단 조건. 업종이 아님'),
    ('OVERSEAS',        NULL,                FALSE, '해외 결제 조건. 업종이 아님');
