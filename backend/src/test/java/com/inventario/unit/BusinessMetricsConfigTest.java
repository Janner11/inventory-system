package com.inventario.unit;

import com.inventario.config.BusinessMetricsConfig;
import com.inventario.entity.MovementType;
import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import com.inventario.repository.ProductRepository;
import com.inventario.repository.StockMovementRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessMetricsConfigTest {

    private ProductRepository productRepository;
    private StockMovementRepository stockMovementRepository;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        stockMovementRepository = mock(StockMovementRepository.class);
        registry = new SimpleMeterRegistry();

        new BusinessMetricsConfig()
                .businessMetrics(productRepository, stockMovementRepository)
                .bindTo(registry);
    }

    private static Product product(String price, int quantity, int minStock) {
        Product product = new Product();
        product.setPrice(new BigDecimal(price));
        product.setQuantity(quantity);
        product.setMinStock(minStock);
        return product;
    }

    @Test
    void productsTotal_reflectsCountByStatus() {
        when(productRepository.countByStatus(ProductStatus.ACTIVE)).thenReturn(12L);
        when(productRepository.countByStatus(ProductStatus.INACTIVE)).thenReturn(3L);

        assertThat(registry.get("products").tag("status", "ACTIVE").gauge().value()).isEqualTo(12.0);
        assertThat(registry.get("products").tag("status", "INACTIVE").gauge().value()).isEqualTo(3.0);
    }

    @Test
    void productsCriticalTotal_reflectsProductsBelowMinStock() {
        when(productRepository.findByQuantityLessThanMinStockAndStatus(ProductStatus.ACTIVE))
                .thenReturn(List.of(product("10.00", 1, 5), product("5.00", 2, 10)));

        assertThat(registry.get("products_critical").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void inventoryValueTotal_sumsPriceTimesQuantityOfActiveProducts() {
        when(productRepository.findByStatus(ProductStatus.ACTIVE))
                .thenReturn(List.of(product("10.00", 3, 1), product("2.50", 4, 1)));

        // 10.00*3 + 2.50*4 = 30 + 10 = 40
        assertThat(registry.get("inventory_value").gauge().value()).isEqualTo(40.0);
    }

    @Test
    void stockMovementsTotal_reflectsCountByType() {
        when(stockMovementRepository.countByType(MovementType.ENTRY)).thenReturn(7L);
        when(stockMovementRepository.countByType(MovementType.EXIT)).thenReturn(4L);
        when(stockMovementRepository.countByType(MovementType.ADJUSTMENT)).thenReturn(1L);

        assertThat(registry.get("stock_movements").tag("type", "ENTRY").gauge().value()).isEqualTo(7.0);
        assertThat(registry.get("stock_movements").tag("type", "EXIT").gauge().value()).isEqualTo(4.0);
        assertThat(registry.get("stock_movements").tag("type", "ADJUSTMENT").gauge().value()).isEqualTo(1.0);
    }
}
