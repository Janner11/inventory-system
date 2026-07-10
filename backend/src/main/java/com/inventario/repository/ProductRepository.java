package com.inventario.repository;

import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    List<Product> findByStatus(ProductStatus status);

    @Query("SELECT p FROM Product p WHERE p.quantity < p.minStock AND p.status = :status")
    List<Product> findByQuantityLessThanMinStockAndStatus(@Param("status") ProductStatus status);
}
