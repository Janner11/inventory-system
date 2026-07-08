package com.inventario.repository;

import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * Filtros dinámicos y combinables para {@code ProductRepository.findAll(Specification, Pageable)},
 * usados por {@code ProductService} para paginación/búsqueda/filtros (BACK-003).
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> withFilters(ProductStatus status, String category,
            BigDecimal minPrice, BigDecimal maxPrice, String search) {
        return Specification.where(hasStatus(status))
                .and(hasCategory(category))
                .and(priceGreaterThanOrEqualTo(minPrice))
                .and(priceLessThanOrEqualTo(maxPrice))
                .and(matchesSearch(search));
    }

    /** Sin status explícito se asume ACTIVE (ADR-001: los listados ocultan INACTIVE por defecto). */
    public static Specification<Product> hasStatus(ProductStatus status) {
        ProductStatus effectiveStatus = status != null ? status : ProductStatus.ACTIVE;
        return (root, query, cb) -> cb.equal(root.get("status"), effectiveStatus);
    }

    public static Specification<Product> hasCategory(String category) {
        return (root, query, cb) -> StringUtils.hasText(category)
                ? cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase())
                : null;
    }

    public static Specification<Product> priceGreaterThanOrEqualTo(BigDecimal minPrice) {
        return (root, query, cb) -> minPrice != null
                ? cb.greaterThanOrEqualTo(root.get("price"), minPrice)
                : null;
    }

    public static Specification<Product> priceLessThanOrEqualTo(BigDecimal maxPrice) {
        return (root, query, cb) -> maxPrice != null
                ? cb.lessThanOrEqualTo(root.get("price"), maxPrice)
                : null;
    }

    public static Specification<Product> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(search)) {
                return null;
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("sku")), pattern),
                    cb.like(cb.lower(root.get("category")), pattern)
            );
        };
    }
}
