package com.inventario.service;

import com.inventario.dto.ProductFilterDTO;
import com.inventario.dto.ProductRequestDTO;
import com.inventario.dto.ProductResponseDTO;
import com.inventario.dto.ProductStatsDTO;
import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import com.inventario.exception.DuplicateSkuException;
import com.inventario.exception.ProductNotFoundException;
import com.inventario.mapper.ProductMapper;
import com.inventario.repository.ProductRepository;
import com.inventario.repository.ProductSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    /**
     * Listado paginado con filtros dinámicos (categoría, status, rango de precio, texto libre).
     * {@code filter} puede ser {@code null} (equivalente a {@link ProductFilterDTO#empty()}).
     */
    public Page<ProductResponseDTO> getAllProducts(Pageable pageable, ProductFilterDTO filter) {
        ProductFilterDTO effectiveFilter = filter != null ? filter : ProductFilterDTO.empty();

        return productRepository.findAll(
                        ProductSpecifications.withFilters(
                                effectiveFilter.status(), effectiveFilter.category(),
                                effectiveFilter.minPrice(), effectiveFilter.maxPrice(), effectiveFilter.search()),
                        pageable)
                .map(productMapper::toResponseDTO);
    }

    public ProductResponseDTO getProductById(UUID id) {
        return productMapper.toResponseDTO(findProductOrThrow(id));
    }

    /** Productos activos de una categoría (ignora mayúsculas/minúsculas; categoría vacía = sin filtrar). */
    public Page<ProductResponseDTO> getProductsByCategory(String category, Pageable pageable) {
        return getAllProducts(pageable, new ProductFilterDTO(null, category, null, null, null));
    }

    /** Búsqueda de texto libre por nombre, SKU o categoría; query vacío = sin filtrar (no lanza NPE). */
    public Page<ProductResponseDTO> searchProducts(String query, Pageable pageable) {
        return getAllProducts(pageable, new ProductFilterDTO(null, null, null, null, query));
    }

    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO request) {
        validateBusinessRules(request);

        String sku = request.sku().toUpperCase();
        ensureSkuIsAvailable(sku, null);

        Product product = productMapper.toEntity(request);
        product.setSku(sku);
        product.setStatus(ProductStatus.ACTIVE);

        Product saved = productRepository.save(product);
        log.info("Producto creado: id={}, sku={}", saved.getId(), saved.getSku());

        return productMapper.toResponseDTO(saved);
    }

    @Transactional
    public ProductResponseDTO updateProduct(UUID id, ProductRequestDTO request) {
        validateBusinessRules(request);
        Product product = findProductOrThrow(id);

        String sku = request.sku().toUpperCase();
        ensureSkuIsAvailable(sku, id);

        product.setName(request.name());
        product.setSku(sku);
        product.setDescription(request.description());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setQuantity(request.quantity());
        product.setMinStock(request.minStock());

        Product saved = productRepository.save(product);
        log.info("Producto actualizado: id={}, sku={}", saved.getId(), saved.getSku());

        return productMapper.toResponseDTO(saved);
    }

    /** Soft delete (ADR-001): nunca borra físicamente, siempre cambia status a INACTIVE. */
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProductOrThrow(id);
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.save(product);
        log.info("Producto eliminado (soft delete): id={}, sku={}", product.getId(), product.getSku());
    }

    public List<ProductResponseDTO> getProductsBelowMinStock() {
        return productRepository.findByQuantityLessThanMinStockAndStatus(ProductStatus.ACTIVE).stream()
                .map(productMapper::toResponseDTO)
                .toList();
    }

    public ProductStatsDTO getProductStats() {
        long activeProducts = productRepository.countByStatus(ProductStatus.ACTIVE);
        long inactiveProducts = productRepository.countByStatus(ProductStatus.INACTIVE);
        long belowMinStockProducts =
                productRepository.findByQuantityLessThanMinStockAndStatus(ProductStatus.ACTIVE).size();

        BigDecimal totalInventoryValue = productRepository.findByStatus(ProductStatus.ACTIVE).stream()
                .map(product -> product.getPrice().multiply(BigDecimal.valueOf(product.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProductStatsDTO(activeProducts + inactiveProducts, activeProducts, inactiveProducts,
                belowMinStockProducts, totalInventoryValue);
    }

    private Product findProductOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private void ensureSkuIsAvailable(String sku, UUID excludingProductId) {
        productRepository.findBySkuIgnoreCase(sku)
                .filter(existing -> !existing.getId().equals(excludingProductId))
                .ifPresent(existing -> {
                    throw new DuplicateSkuException(sku);
                });
    }

    /**
     * Guarda de negocio a nivel de servicio, independiente de la validacion Bean Validation
     * (@Valid) del controller: protege la invariante aunque el metodo se invoque directamente
     * (otros servicios, tests, futuros consumidores) sin pasar por el controller.
     */
    private void validateBusinessRules(ProductRequestDTO request) {
        if (!StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }
        if (!StringUtils.hasText(request.sku())) {
            throw new IllegalArgumentException("El SKU del producto es obligatorio");
        }
        if (!StringUtils.hasText(request.category())) {
            throw new IllegalArgumentException("La categoria del producto es obligatoria");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que cero");
        }
        if (request.quantity() == null || request.quantity() < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa");
        }
        if (request.minStock() == null || request.minStock() < 0) {
            throw new IllegalArgumentException("El stock minimo no puede ser negativo");
        }
    }
}
