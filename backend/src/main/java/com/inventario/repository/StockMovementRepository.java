package com.inventario.repository;

import com.inventario.entity.MovementType;
import com.inventario.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("SELECT m FROM StockMovement m WHERE m.product.id = :productId ORDER BY m.createdAt DESC")
    Page<StockMovement> findByProductId(@Param("productId") UUID productId, Pageable pageable);

    Page<StockMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByType(MovementType type);

    /**
     * Productos con mas movimientos desde {@code since}, ordenados de mayor a menor cantidad de
     * movimientos. Cada fila es {@code [productId (UUID), sku (String), name (String), count (Long)]};
     * el tamano del resultado se acota con {@code pageable} (offset/sort del Pageable se ignoran,
     * solo se usa como limite via {@link Pageable#getPageSize()}).
     */
    @Query("""
            SELECT m.product.id, m.product.sku, m.product.name, COUNT(m)
            FROM StockMovement m
            WHERE m.createdAt >= :since
            GROUP BY m.product.id, m.product.sku, m.product.name
            ORDER BY COUNT(m) DESC
            """)
    List<Object[]> findTopMovedProductsSince(@Param("since") LocalDateTime since, Pageable pageable);
}
