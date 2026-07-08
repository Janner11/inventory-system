package com.inventario.repository;

import com.inventario.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("SELECT m FROM StockMovement m WHERE m.product.id = :productId ORDER BY m.createdAt DESC")
    Page<StockMovement> findByProductId(@Param("productId") UUID productId, Pageable pageable);

    Page<StockMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
