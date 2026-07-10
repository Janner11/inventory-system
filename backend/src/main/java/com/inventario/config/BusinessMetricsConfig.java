package com.inventario.config;

import com.inventario.entity.MovementType;
import com.inventario.entity.ProductStatus;
import com.inventario.repository.ProductRepository;
import com.inventario.repository.StockMovementRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Metricas de negocio (OBS-004, dashboard "Negocio"): expuestas junto con las metricas
 * tecnicas (HTTP, JVM, HikariCP) en /actuator/prometheus, sin ningun endpoint nuevo.
 * Los valores se recalculan contra la base de datos en cada scrape de Prometheus (15s,
 * prometheus.yml) - suficiente para el volumen de datos de este proyecto.
 */
@Configuration
public class BusinessMetricsConfig {

    @Bean
    public MeterBinder businessMetrics(ProductRepository productRepository,
                                        StockMovementRepository stockMovementRepository) {
        return registry -> {
            Gauge.builder("products", productRepository,
                            repo -> (double) repo.countByStatus(ProductStatus.ACTIVE))
                    .tag("status", "ACTIVE")
                    .description("Cantidad de productos por status")
                    .register(registry);
            Gauge.builder("products", productRepository,
                            repo -> (double) repo.countByStatus(ProductStatus.INACTIVE))
                    .tag("status", "INACTIVE")
                    .description("Cantidad de productos por status")
                    .register(registry);

            Gauge.builder("products_critical", productRepository,
                            repo -> (double) repo.findByQuantityLessThanMinStockAndStatus(ProductStatus.ACTIVE).size())
                    .description("Productos activos con cantidad bajo su stock minimo")
                    .register(registry);

            Gauge.builder("inventory_value", productRepository,
                            repo -> repo.findByStatus(ProductStatus.ACTIVE).stream()
                                    .map(product -> product.getPrice().multiply(BigDecimal.valueOf(product.getQuantity())))
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .doubleValue())
                    .description("Valor total del inventario activo (precio x cantidad)")
                    .register(registry);

            for (MovementType type : MovementType.values()) {
                Gauge.builder("stock_movements", stockMovementRepository,
                                repo -> (double) repo.countByType(type))
                        .tag("type", type.name())
                        .description("Cantidad historica de movimientos de stock por tipo")
                        .register(registry);
            }
        };
    }
}
