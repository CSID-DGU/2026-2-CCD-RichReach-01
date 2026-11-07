package com.richreach.card.repository;

import com.richreach.card.entity.CardProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardProductRepository extends JpaRepository<CardProduct, Long> {

    /** 한 카드사의 모든 카드 상품. 보유카드를 상품에 매칭할 때 후보로 쓴다. */
    List<CardProduct> findByIssuerCode(String issuerCode);

}
