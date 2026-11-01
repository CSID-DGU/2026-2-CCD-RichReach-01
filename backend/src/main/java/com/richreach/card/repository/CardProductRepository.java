package com.richreach.card.repository;

import com.richreach.card.entity.CardProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardProductRepository extends JpaRepository<CardProduct, Long> {
}
