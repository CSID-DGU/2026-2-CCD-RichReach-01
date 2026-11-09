-- 가맹점명 → 화면용 6개 업종 분류 규칙. 거래를 저장할 때 가맹점명으로 업종을 정하는 데 쓴다. (이슈 #163)
--
-- 규칙은 코드가 아니라 데이터라서 새 마이그레이션으로 행을 바꾸거나 추가하면 된다. 아래는 첫 안이고 실제 가맹점 데이터를
-- 보면서 보완한다.
--
-- 적용 방식
--  * 가맹점명과 pattern은 같은 방식으로 정규화해서 비교한다: 전각·반각과 대소문자를 맞추고, (주)와 주식회사 표기를 지우고,
--    공백·특수문자를 모두 지운다. 그래서 'GS25 강남점', 'gs25강남점', '(주)지에스리테일'이 같은 규칙에 걸린다.
--  * match_type: CONTAINS(포함), PREFIX(가맹점명이 pattern으로 시작), EXACT(정확히 일치).
--    짧은 영문 상호(예: CU)는 다른 단어에 섞여 오탐하지 않게 PREFIX로 쓴다.
--  * 규칙은 priority가 작은 것부터 보고, 같으면 pattern이 긴 것(더 구체적인 것)부터 본다. 먼저 걸린 규칙이 이긴다.
--    예: '쿠팡이츠'(음식점)가 '쿠팡'(쇼핑)보다 먼저 걸린다.
--  * 어떤 규칙에도 안 걸리면 ETC다. (코드에서 처리하며 이 표에는 없다)
--
-- priority: 50 이하는 오탐을 막는 예외 규칙, 100은 상호(브랜드), 200과 250은 일반 단어다.
-- 규칙에 없는 업종(영화, 여행, 병원 등)은 ETC가 맞아서 따로 만들지 않는다.
CREATE TABLE merchant_category_rules (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    pattern     VARCHAR(100) NOT NULL COMMENT '가맹점명과 비교할 문구. 비교할 때 정규화한다',
    match_type  VARCHAR(20)  NOT NULL DEFAULT 'CONTAINS' COMMENT 'CONTAINS, PREFIX, EXACT',
    ui_category VARCHAR(30)  NOT NULL COMMENT '화면용 6개 업종 코드',
    priority    INT          NOT NULL DEFAULT 100 COMMENT '작은 것부터 적용. 같으면 pattern이 긴 것부터',
    active      BOOLEAN      NOT NULL DEFAULT TRUE COMMENT 'FALSE면 쓰지 않는다. 행을 지우지 않고 끌 때 쓴다',
    note        VARCHAR(200) NULL COMMENT '설명',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_category_rules_pattern_type (pattern, match_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 오탐을 막는 예외 규칙 (priority 40, 50)
INSERT INTO merchant_category_rules (pattern, match_type, ui_category, priority, note) VALUES
    ('smartstore', 'CONTAINS', 'SHOPPING', 40, '네이버 스마트스토어. 아래 스마트 예외보다 먼저 본다'),
    ('스마트스토어', 'CONTAINS', 'SHOPPING', 40, '네이버 스마트스토어. 아래 스마트 예외보다 먼저 본다'),
    ('스마트', 'CONTAINS', 'ETC', 50, '마트로 시작하는 일반 규칙에 스마트폰·스마트스터디 등이 걸리지 않게 한다'),
    ('culture', 'PREFIX', 'ETC', 50, 'cu로 시작하지만 편의점이 아니다 (컬처랜드 등)');

-- 카페 상호 (priority 100)
INSERT INTO merchant_category_rules (pattern, match_type, ui_category, priority, note) VALUES
    ('스타벅스', 'CONTAINS', 'CAFE', 100, NULL),
    ('starbucks', 'CONTAINS', 'CAFE', 100, NULL),
    ('에스씨케이컴퍼니', 'CONTAINS', 'CAFE', 100, '스타벅스 법인명'),
    ('투썸플레이스', 'CONTAINS', 'CAFE', 100, NULL),
    ('투썸', 'CONTAINS', 'CAFE', 100, NULL),
    ('이디야', 'CONTAINS', 'CAFE', 100, NULL),
    ('메가커피', 'CONTAINS', 'CAFE', 100, NULL),
    ('메가엠지씨', 'CONTAINS', 'CAFE', 100, '메가커피 법인명'),
    ('컴포즈', 'CONTAINS', 'CAFE', 100, NULL),
    ('빽다방', 'CONTAINS', 'CAFE', 100, NULL),
    ('할리스', 'CONTAINS', 'CAFE', 100, NULL),
    ('폴바셋', 'CONTAINS', 'CAFE', 100, NULL),
    ('커피빈', 'CONTAINS', 'CAFE', 100, NULL),
    ('탐앤탐스', 'CONTAINS', 'CAFE', 100, NULL),
    ('엔제리너스', 'CONTAINS', 'CAFE', 100, NULL),
    ('파스쿠찌', 'CONTAINS', 'CAFE', 100, NULL),
    ('공차', 'CONTAINS', 'CAFE', 100, NULL),
    ('설빙', 'CONTAINS', 'CAFE', 100, '디저트 카페'),
    ('던킨', 'CONTAINS', 'CAFE', 100, '도넛·커피'),
    ('배스킨라빈스', 'CONTAINS', 'CAFE', 100, '디저트. 카페로 본다'),
    ('파리바게뜨', 'CONTAINS', 'CAFE', 100, '베이커리는 카페로 본다 (혜택 업종 대응표와 같은 기준)'),
    ('파리바게트', 'CONTAINS', 'CAFE', 100, '표기 변형'),
    ('뚜레쥬르', 'CONTAINS', 'CAFE', 100, '베이커리'),
    ('성심당', 'CONTAINS', 'CAFE', 100, '베이커리');

-- 음식점 상호 (priority 100)
INSERT INTO merchant_category_rules (pattern, match_type, ui_category, priority, note) VALUES
    ('배달의민족', 'CONTAINS', 'DINING', 100, '배달앱은 음식점으로 본다'),
    ('배민', 'CONTAINS', 'DINING', 100, NULL),
    ('우아한형제들', 'CONTAINS', 'DINING', 100, '배달의민족 법인명'),
    ('요기요', 'CONTAINS', 'DINING', 100, NULL),
    ('쿠팡이츠', 'CONTAINS', 'DINING', 100, '쿠팡(쇼핑)보다 긴 패턴이라 먼저 걸린다'),
    ('맥도날드', 'CONTAINS', 'DINING', 100, NULL),
    ('롯데리아', 'CONTAINS', 'DINING', 100, NULL),
    ('버거킹', 'CONTAINS', 'DINING', 100, NULL),
    ('kfc', 'CONTAINS', 'DINING', 100, NULL),
    ('맘스터치', 'CONTAINS', 'DINING', 100, NULL),
    ('서브웨이', 'CONTAINS', 'DINING', 100, NULL),
    ('도미노피자', 'CONTAINS', 'DINING', 100, NULL),
    ('피자헛', 'CONTAINS', 'DINING', 100, NULL),
    ('파파존스', 'CONTAINS', 'DINING', 100, NULL),
    ('교촌', 'CONTAINS', 'DINING', 100, NULL),
    ('bbq', 'CONTAINS', 'DINING', 100, NULL),
    ('bhc', 'CONTAINS', 'DINING', 100, NULL),
    ('굽네', 'CONTAINS', 'DINING', 100, NULL),
    ('네네치킨', 'CONTAINS', 'DINING', 100, NULL),
    ('김밥천국', 'CONTAINS', 'DINING', 100, NULL),
    ('본죽', 'CONTAINS', 'DINING', 100, NULL),
    ('한솥', 'CONTAINS', 'DINING', 100, NULL),
    ('아웃백', 'CONTAINS', 'DINING', 100, NULL),
    ('빕스', 'CONTAINS', 'DINING', 100, NULL),
    ('애슐리', 'CONTAINS', 'DINING', 100, NULL),
    ('이삭토스트', 'CONTAINS', 'DINING', 100, NULL),
    ('쿠우쿠우', 'CONTAINS', 'DINING', 100, NULL),
    ('명륜진사갈비', 'CONTAINS', 'DINING', 100, NULL),
    ('새마을식당', 'CONTAINS', 'DINING', 100, NULL),
    ('노브랜드버거', 'CONTAINS', 'DINING', 100, NULL);

-- 쇼핑 상호 (priority 100)
INSERT INTO merchant_category_rules (pattern, match_type, ui_category, priority, note) VALUES
    ('쿠팡', 'CONTAINS', 'SHOPPING', 100, '쿠팡이츠는 더 긴 패턴이라 먼저 걸려 음식점이 된다'),
    ('coupang', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('11번가', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('g마켓', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('지마켓', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('이베이코리아', 'CONTAINS', 'SHOPPING', 100, 'G마켓·옥션 법인명'),
    ('옥션', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('위메프', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('티몬', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('무신사', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('올리브영', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('이마트', 'CONTAINS', 'SHOPPING', 100, '이마트24는 더 긴 패턴이라 먼저 걸려 편의점이 된다'),
    ('홈플러스', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('롯데마트', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('코스트코', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('다이소', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('마켓컬리', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('ssg', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('에스에스지닷컴', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('오아시스', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('하나로마트', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('롯데백화점', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('현대백화점', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('신세계', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('갤러리아', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('롯데쇼핑', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('알라딘', 'CONTAINS', 'SHOPPING', 100, '도서'),
    ('교보문고', 'CONTAINS', 'SHOPPING', 100, '도서'),
    ('예스24', 'CONTAINS', 'SHOPPING', 100, '도서'),
    ('영풍문고', 'CONTAINS', 'SHOPPING', 100, '도서'),
    ('에이블리', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('지그재그', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('오늘의집', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('롯데온', 'CONTAINS', 'SHOPPING', 100, NULL),
    ('네이버쇼핑', 'CONTAINS', 'SHOPPING', 100, NULL);

-- 교통 상호 (priority 100)
INSERT INTO merchant_category_rules (pattern, match_type, ui_category, priority, note) VALUES
    ('티머니', 'CONTAINS', 'TRANSPORT', 100, '교통카드'),
    ('카카오t', 'CONTAINS', 'TRANSPORT', 100, '카카오 택시'),
    ('카카오모빌리티', 'CONTAINS', 'TRANSPORT', 100, NULL),
    ('코레일', 'CONTAINS', 'TRANSPORT', 100, NULL),
    ('한국철도', 'CONTAINS', 'TRANSPORT', 100, NULL),
    ('srt', 'CONTAINS', 'TRANSPORT', 100, NULL),
    ('서울교통공사', 'CONTAINS', 'TRANSPORT', 100, NULL),
    ('하이패스', 'CONTAINS', 'TRANSPORT', 100, NULL),
    ('한국도로공사', 'CONTAINS', 'TRANSPORT', 100, NULL),
    ('gs칼텍스', 'CONTAINS', 'TRANSPORT', 100, '주유'),
    ('지에스칼텍스', 'CONTAINS', 'TRANSPORT', 100, '주유'),
    ('sk에너지', 'CONTAINS', 'TRANSPORT', 100, '주유'),
    ('s-oil', 'CONTAINS', 'TRANSPORT', 100, '주유'),
    ('에쓰오일', 'CONTAINS', 'TRANSPORT', 100, '주유'),
    ('현대오일뱅크', 'CONTAINS', 'TRANSPORT', 100, '주유'),
    ('알뜰주유소', 'CONTAINS', 'TRANSPORT', 100, '주유'),
    ('쏘카', 'CONTAINS', 'TRANSPORT', 100, NULL),
    ('그린카', 'CONTAINS', 'TRANSPORT', 100, NULL),
    ('따릉이', 'CONTAINS', 'TRANSPORT', 100, NULL);

-- 편의점 상호 (priority 100)
INSERT INTO merchant_category_rules (pattern, match_type, ui_category, priority, note) VALUES
    ('gs25', 'CONTAINS', 'CONVENIENCE_STORE', 100, NULL),
    ('지에스25', 'CONTAINS', 'CONVENIENCE_STORE', 100, NULL),
    ('지에스리테일', 'CONTAINS', 'CONVENIENCE_STORE', 100, 'GS25 법인명'),
    ('세븐일레븐', 'CONTAINS', 'CONVENIENCE_STORE', 100, NULL),
    ('코리아세븐', 'CONTAINS', 'CONVENIENCE_STORE', 100, '세븐일레븐 법인명'),
    ('7eleven', 'CONTAINS', 'CONVENIENCE_STORE', 100, NULL),
    ('이마트24', 'CONTAINS', 'CONVENIENCE_STORE', 100, '이마트(쇼핑)보다 긴 패턴이라 먼저 걸린다'),
    ('미니스톱', 'CONTAINS', 'CONVENIENCE_STORE', 100, NULL),
    ('씨유', 'CONTAINS', 'CONVENIENCE_STORE', 100, NULL),
    ('비지에프리테일', 'CONTAINS', 'CONVENIENCE_STORE', 100, 'CU 법인명'),
    ('bgf리테일', 'CONTAINS', 'CONVENIENCE_STORE', 100, 'CU 법인명'),
    ('cu', 'PREFIX', 'CONVENIENCE_STORE', 100, '짧은 영문 상호라 접두어로만 본다');

-- 일반 단어 (priority 200). 상호 규칙에 걸리지 않은 가맹점을 업종 단어로 분류한다.
INSERT INTO merchant_category_rules (pattern, match_type, ui_category, priority, note) VALUES
    ('식당', 'CONTAINS', 'DINING', 200, NULL),
    ('치킨', 'CONTAINS', 'DINING', 200, NULL),
    ('피자', 'CONTAINS', 'DINING', 200, NULL),
    ('족발', 'CONTAINS', 'DINING', 200, NULL),
    ('보쌈', 'CONTAINS', 'DINING', 200, NULL),
    ('곱창', 'CONTAINS', 'DINING', 200, NULL),
    ('삼겹살', 'CONTAINS', 'DINING', 200, NULL),
    ('갈비', 'CONTAINS', 'DINING', 200, NULL),
    ('국밥', 'CONTAINS', 'DINING', 200, NULL),
    ('냉면', 'CONTAINS', 'DINING', 200, NULL),
    ('분식', 'CONTAINS', 'DINING', 200, NULL),
    ('김밥', 'CONTAINS', 'DINING', 200, NULL),
    ('레스토랑', 'CONTAINS', 'DINING', 200, NULL),
    ('포차', 'CONTAINS', 'DINING', 200, NULL),
    ('호프', 'CONTAINS', 'DINING', 200, NULL),
    ('주점', 'CONTAINS', 'DINING', 200, NULL),
    ('횟집', 'CONTAINS', 'DINING', 200, NULL),
    ('초밥', 'CONTAINS', 'DINING', 200, NULL),
    ('라멘', 'CONTAINS', 'DINING', 200, NULL),
    ('칼국수', 'CONTAINS', 'DINING', 200, NULL),
    ('설렁탕', 'CONTAINS', 'DINING', 200, NULL),
    ('해장국', 'CONTAINS', 'DINING', 200, NULL),
    ('마라탕', 'CONTAINS', 'DINING', 200, NULL),
    ('샤브샤브', 'CONTAINS', 'DINING', 200, NULL),
    ('이자카야', 'CONTAINS', 'DINING', 200, NULL),
    ('뷔페', 'CONTAINS', 'DINING', 200, NULL),
    ('도시락', 'CONTAINS', 'DINING', 200, NULL),
    ('카페', 'CONTAINS', 'CAFE', 200, NULL),
    ('커피', 'CONTAINS', 'CAFE', 200, NULL),
    ('베이커리', 'CONTAINS', 'CAFE', 200, NULL),
    ('제과', 'CONTAINS', 'CAFE', 200, NULL),
    ('디저트', 'CONTAINS', 'CAFE', 200, NULL),
    ('빵집', 'CONTAINS', 'CAFE', 200, NULL),
    ('마카롱', 'CONTAINS', 'CAFE', 200, NULL),
    ('도넛', 'CONTAINS', 'CAFE', 200, NULL),
    ('백화점', 'CONTAINS', 'SHOPPING', 200, NULL),
    ('아울렛', 'CONTAINS', 'SHOPPING', 200, NULL),
    ('쇼핑', 'CONTAINS', 'SHOPPING', 200, NULL),
    ('서점', 'CONTAINS', 'SHOPPING', 200, NULL),
    ('화장품', 'CONTAINS', 'SHOPPING', 200, NULL),
    ('의류', 'CONTAINS', 'SHOPPING', 200, NULL),
    ('패션', 'CONTAINS', 'SHOPPING', 200, NULL),
    ('버스', 'CONTAINS', 'TRANSPORT', 200, NULL),
    ('택시', 'CONTAINS', 'TRANSPORT', 200, NULL),
    ('지하철', 'CONTAINS', 'TRANSPORT', 200, NULL),
    ('철도', 'CONTAINS', 'TRANSPORT', 200, NULL),
    ('주유', 'CONTAINS', 'TRANSPORT', 200, NULL),
    ('주차', 'CONTAINS', 'TRANSPORT', 200, NULL),
    ('충전소', 'CONTAINS', 'TRANSPORT', 200, NULL),
    ('고속도로', 'CONTAINS', 'TRANSPORT', 200, NULL),
    ('통행료', 'CONTAINS', 'TRANSPORT', 200, NULL),
    ('편의점', 'CONTAINS', 'CONVENIENCE_STORE', 200, NULL),
    ('마트', 'CONTAINS', 'SHOPPING', 250, '동네 마트. 스마트 등이 걸리지 않게 예외 규칙(스마트)을 둔다');
