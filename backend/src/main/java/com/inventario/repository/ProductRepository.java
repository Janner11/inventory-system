package com.inventario.repository;

import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    List<Product> findByStatus(ProductStatus status);
}
