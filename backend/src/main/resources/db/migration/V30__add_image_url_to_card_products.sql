-- 카드 이미지 주소. 카드사 공식 페이지의 카드 앞면 이미지를 가리키는 주소만 저장한다. (이미지 파일은 저장하지 않는다)
-- NULL이면 이미지를 아직 모르는 카드다. 승격(CardPromotionService)은 이 컬럼을 건드리지 않아서, 카드 상품을 다시 승격해도
-- 적재한 이미지 주소가 지워지지 않는다. 값은 CardImageLoader가 card_images.csv로 채운다.
ALTER TABLE card_products
    ADD COLUMN image_url VARCHAR(500) NULL COMMENT '카드 앞면 이미지 주소(https). 없으면 NULL' AFTER source_url;
