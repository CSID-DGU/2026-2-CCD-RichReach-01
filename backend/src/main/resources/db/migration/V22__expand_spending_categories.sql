-- 소비 업종을 6개에서 11개로 확장한다. (이슈 #183)
-- 기존 6개(DINING, CAFE, SHOPPING, TRANSPORT, CONVENIENCE_STORE, ETC)의 코드는 그대로 두고,
-- FUEL_VEHICLE(주유·차량), LIFE_HEALTH(생활·건강), TELECOM_SUBSCRIPTION(통신·정기결제),
-- CULTURE_TRAVEL(문화·여행), EDUCATION(교육·도서)을 더한다.
--
--  * 혜택 업종 대응표(benefit_category_mappings)와 가맹점 분류 규칙(merchant_category_rules)을 새 업종에 맞춘다.
--  * 이미 저장된 거래의 업종은 이 마이그레이션이 바꾸지 않는다. 배포 후 재분류(TransactionReclassifyService)를 한 번 돌려야
--    기존 거래가 새 업종으로 바뀐다. 사용자가 고친 업종(category_override)은 재분류가 건드리지 않는다.

ALTER TABLE benefit_category_mappings
    MODIFY ui_category VARCHAR(30) NULL COMMENT '화면용 11개 업종 코드. 업종이 아닌 코드는 NULL';
ALTER TABLE merchant_category_rules
    MODIFY ui_category VARCHAR(30) NOT NULL COMMENT '화면용 11개 업종 코드';

-- 1. 혜택 업종 대응표
UPDATE benefit_category_mappings SET ui_category = 'FUEL_VEHICLE'
    WHERE category_code IN ('FUEL', 'EV_CHARGING', 'AUTOMOTIVE');
UPDATE benefit_category_mappings SET ui_category = 'CONVENIENCE_STORE'
    WHERE category_code IN ('GROCERY');
UPDATE benefit_category_mappings SET ui_category = 'LIFE_HEALTH'
    WHERE category_code IN ('HEALTH', 'BEAUTY', 'PET');
UPDATE benefit_category_mappings SET ui_category = 'TELECOM_SUBSCRIPTION'
    WHERE category_code IN ('TELECOM', 'UTILITIES', 'INSURANCE', 'SUBSCRIPTION', 'DIGITAL_CONTENT');
UPDATE benefit_category_mappings SET ui_category = 'CULTURE_TRAVEL'
    WHERE category_code IN ('MOVIE', 'LEISURE', 'TRAVEL');
UPDATE benefit_category_mappings SET ui_category = 'EDUCATION'
    WHERE category_code IN ('EDUCATION', 'BOOKS');

-- 2. 가맹점 분류 규칙: 기존 규칙 중 업종이 바뀌는 것
UPDATE merchant_category_rules SET ui_category = 'FUEL_VEHICLE'
    WHERE match_type = 'CONTAINS'
      AND pattern IN ('gs칼텍스', '지에스칼텍스', 'sk에너지', 's-oil', '에쓰오일', '현대오일뱅크', '알뜰주유소', '주유',
                      '충전소', '주차');
UPDATE merchant_category_rules SET ui_category = 'CONVENIENCE_STORE'
    WHERE match_type = 'CONTAINS'
      AND pattern IN ('이마트', '홈플러스', '롯데마트', '코스트코', '마켓컬리', '오아시스', '하나로마트', '마트');
UPDATE merchant_category_rules SET ui_category = 'EDUCATION'
    WHERE match_type = 'CONTAINS'
      AND pattern IN ('알라딘', '교보문고', '예스24', '영풍문고', '서점');
UPDATE merchant_category_rules SET ui_category = 'LIFE_HEALTH'
    WHERE match_type = 'CONTAINS'
      AND pattern IN ('올리브영', '화장품');

-- 3. 가맹점 분류 규칙: 새 업종의 규칙
INSERT INTO merchant_category_rules (pattern, match_type, ui_category, priority, note) VALUES
    ('병원', 'CONTAINS', 'LIFE_HEALTH', 200, NULL),
    ('의원', 'CONTAINS', 'LIFE_HEALTH', 200, NULL),
    ('약국', 'CONTAINS', 'LIFE_HEALTH', 200, NULL),
    ('치과', 'CONTAINS', 'LIFE_HEALTH', 200, NULL),
    ('한의원', 'CONTAINS', 'LIFE_HEALTH', 200, NULL),
    ('안경', 'CONTAINS', 'LIFE_HEALTH', 200, NULL),
    ('동물병원', 'CONTAINS', 'LIFE_HEALTH', 100, '반려동물. 병원보다 긴 패턴이라 먼저 걸린다'),
    ('미용실', 'CONTAINS', 'LIFE_HEALTH', 200, '뷰티'),
    ('네일', 'CONTAINS', 'LIFE_HEALTH', 200, '뷰티'),
    ('다비치', 'CONTAINS', 'LIFE_HEALTH', 100, '안경'),
    ('에스케이텔레콤', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, 'SKT 법인명'),
    ('skt', 'PREFIX', 'TELECOM_SUBSCRIPTION', 100, '짧은 영문 상호라 접두어로만 본다'),
    ('케이티', 'PREFIX', 'TELECOM_SUBSCRIPTION', 100, 'KT'),
    ('유플러스', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, 'LG유플러스'),
    ('알뜰폰', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, NULL),
    ('통신', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 200, NULL),
    ('한국전력', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '공과금'),
    ('도시가스', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '공과금'),
    ('상수도', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '공과금'),
    ('관리비', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 200, NULL),
    ('보험', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 200, NULL),
    ('삼성화재', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '보험'),
    ('현대해상', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '보험'),
    ('한화생명', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '보험'),
    ('삼성생명', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '보험'),
    ('교보생명', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '보험'),
    ('넷플릭스', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '구독'),
    ('netflix', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '구독'),
    ('유튜브프리미엄', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '구독'),
    ('스포티파이', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '구독'),
    ('티빙', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '구독'),
    ('디즈니플러스', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '구독'),
    ('멜론', 'CONTAINS', 'TELECOM_SUBSCRIPTION', 100, '구독'),
    ('cgv', 'CONTAINS', 'CULTURE_TRAVEL', 100, '영화'),
    ('롯데시네마', 'CONTAINS', 'CULTURE_TRAVEL', 100, '영화'),
    ('메가박스', 'CONTAINS', 'CULTURE_TRAVEL', 100, '영화'),
    ('영화', 'CONTAINS', 'CULTURE_TRAVEL', 200, NULL),
    ('공연', 'CONTAINS', 'CULTURE_TRAVEL', 200, NULL),
    ('대한항공', 'CONTAINS', 'CULTURE_TRAVEL', 100, '항공'),
    ('아시아나', 'CONTAINS', 'CULTURE_TRAVEL', 100, '항공'),
    ('제주항공', 'CONTAINS', 'CULTURE_TRAVEL', 100, '항공'),
    ('진에어', 'CONTAINS', 'CULTURE_TRAVEL', 100, '항공'),
    ('티웨이', 'CONTAINS', 'CULTURE_TRAVEL', 100, '항공'),
    ('에어부산', 'CONTAINS', 'CULTURE_TRAVEL', 100, '항공'),
    ('야놀자', 'CONTAINS', 'CULTURE_TRAVEL', 100, '숙박'),
    ('여기어때', 'CONTAINS', 'CULTURE_TRAVEL', 100, '숙박'),
    ('에어비앤비', 'CONTAINS', 'CULTURE_TRAVEL', 100, '숙박'),
    ('호텔', 'CONTAINS', 'CULTURE_TRAVEL', 200, '숙박'),
    ('리조트', 'CONTAINS', 'CULTURE_TRAVEL', 200, '숙박'),
    ('숙박', 'CONTAINS', 'CULTURE_TRAVEL', 200, NULL),
    ('여행', 'CONTAINS', 'CULTURE_TRAVEL', 200, NULL),
    ('에버랜드', 'CONTAINS', 'CULTURE_TRAVEL', 100, '레저'),
    ('롯데월드', 'CONTAINS', 'CULTURE_TRAVEL', 100, '레저'),
    ('학원', 'CONTAINS', 'EDUCATION', 200, NULL),
    ('교육', 'CONTAINS', 'EDUCATION', 200, NULL),
    ('메가스터디', 'CONTAINS', 'EDUCATION', 100, NULL),
    ('이투스', 'CONTAINS', 'EDUCATION', 100, NULL),
    ('클래스101', 'CONTAINS', 'EDUCATION', 100, NULL);
