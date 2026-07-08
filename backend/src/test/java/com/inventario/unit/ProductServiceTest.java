package com.inventario.unit;

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
import com.inventario.service.ProductService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    private ProductService productService;
    private Validator validator;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, productMapper);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void createProduct_withValidData_returnsCreatedProduct() {
        ProductRequestDTO request = buildRequestDTO("lap-001");
        Product mappedEntity = new Product();
        Product savedEntity = buildEntity(UUID.randomUUID(), "LAP-001", ProductStatus.ACTIVE);
        ProductResponseDTO expectedResponse = buildResponseDTO(savedEntity);

        when(productRepository.findBySkuIgnoreCase("LAP-001")).thenReturn(Optional.empty());
        when(productMapper.toEntity(request)).thenReturn(mappedEntity);
        when(productRepository.save(any(Product.class))).thenReturn(savedEntity);
        when(productMapper.toResponseDTO(savedEntity)).thenReturn(expectedResponse);

        ProductResponseDTO result = productService.createProduct(request);

        assertThat(result).isEqualTo(expectedResponse);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSku()).isEqualTo("LAP-001");
        assertThat(captor.getValue().getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void createProduct_withDuplicateSku_throwsDuplicateSkuException() {
        ProductRequestDTO request = buildRequestDTO("DUP-001");
        when(productRepository.findBySkuIgnoreCase("DUP-001"))
                .thenReturn(Optional.of(buildEntity(UUID.randomUUID(), "DUP-001", ProductStatus.ACTIVE)));

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateSkuException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void getProductById_withExistingId_returnsProduct() {
        UUID id = UUID.randomUUID();
        Product entity = buildEntity(id, "MOU-002", ProductStatus.ACTIVE);
        ProductResponseDTO expected = buildResponseDTO(entity);

        when(productRepository.findById(id)).thenReturn(Optional.of(entity));
        when(productMapper.toResponseDTO(entity)).thenReturn(expected);

        ProductResponseDTO result = productService.getProductById(id);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getProductById_withNonExistingId_throwsProductNotFoundException() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(id))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateProduct_withValidData_returnsUpdatedProduct() {
        UUID id = UUID.randomUUID();
        Product existing = buildEntity(id, "MON-004", ProductStatus.ACTIVE);
        ProductRequestDTO request = new ProductRequestDTO("Monitor 27\"", "mon-004", "Nueva descripcion",
                "Perifericos", new BigDecimal("250.00"), 15, 3);
        ProductResponseDTO expected = buildResponseDTO(existing);

        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        when(productRepository.findBySkuIgnoreCase("MON-004")).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);
        when(productMapper.toResponseDTO(existing)).thenReturn(expected);

        ProductResponseDTO result = productService.updateProduct(id, request);

        assertThat(result).isEqualTo(expected);
        assertThat(existing.getName()).isEqualTo("Monitor 27\"");
        assertThat(existing.getSku()).isEqualTo("MON-004");
        assertThat(existing.getDescription()).isEqualTo("Nueva descripcion");
        assertThat(existing.getCategory()).isEqualTo("Perifericos");
        assertThat(existing.getPrice()).isEqualTo(new BigDecimal("250.00"));
        assertThat(existing.getQuantity()).isEqualTo(15);
        assertThat(existing.getMinStock()).isEqualTo(3);
    }

    @Test
    void updateProduct_withNonExistingId_throwsProductNotFoundException() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(id, buildRequestDTO("ANY-001")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateProduct_withDuplicateSkuFromAnotherProduct_throwsDuplicateSkuException() {
        UUID id = UUID.randomUUID();
        Product existing = buildEntity(id, "OLD-005", ProductStatus.ACTIVE);
        Product other = buildEntity(UUID.randomUUID(), "NEW-006", ProductStatus.ACTIVE);
        ProductRequestDTO request = buildRequestDTO("new-006");

        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        when(productRepository.findBySkuIgnoreCase("NEW-006")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> productService.updateProduct(id, request))
                .isInstanceOf(DuplicateSkuException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteProduct_withExistingId_setsStatusInactive() {
        UUID id = UUID.randomUUID();
        Product existing = buildEntity(id, "DEL-007", ProductStatus.ACTIVE);

        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        productService.deleteProduct(id);

        assertThat(existing.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        verify(productRepository).save(existing);
    }

    @Test
    void deleteProduct_withNonExistingId_throwsProductNotFoundException() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(id))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void getProductsBelowMinStock_returnsOnlyAlertProducts() {
        Product lowStock = buildEntity(UUID.randomUUID(), "LOW-008", ProductStatus.ACTIVE);
        lowStock.setQuantity(1);
        lowStock.setMinStock(5);
        ProductResponseDTO responseDTO = buildResponseDTO(lowStock);

        when(productRepository.findByQuantityLessThanMinStockAndStatus(ProductStatus.ACTIVE))
                .thenReturn(List.of(lowStock));
        when(productMapper.toResponseDTO(lowStock)).thenReturn(responseDTO);

        List<ProductResponseDTO> result = productService.getProductsBelowMinStock();

        assertThat(result).containsExactly(responseDTO);
    }

    @Test
    void createProduct_normalizesSkuToUppercase() {
        ProductRequestDTO request = buildRequestDTO("lap-777");
        when(productRepository.findBySkuIgnoreCase("LAP-777")).thenReturn(Optional.empty());
        when(productMapper.toEntity(request)).thenReturn(new Product());
        when(productRepository.save(any(Product.class)))
                .thenReturn(buildEntity(UUID.randomUUID(), "LAP-777", ProductStatus.ACTIVE));

        productService.createProduct(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSku()).isEqualTo("LAP-777");
    }

    @Test
    void createProduct_withNegativeQuantity_throwsIllegalArgumentExceptionBeforeTouchingRepository() {
        ProductRequestDTO request = new ProductRequestDTO(
                "Producto Test", "SKU-900", "Descripcion", "General", new BigDecimal("10.00"), -1, 1);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).findBySkuIgnoreCase(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_withNegativeMinStock_throwsIllegalArgumentException() {
        ProductRequestDTO request = new ProductRequestDTO(
                "Producto Test", "SKU-901", "Descripcion", "General", new BigDecimal("10.00"), 5, -1);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_withNonPositivePrice_throwsIllegalArgumentException() {
        ProductRequestDTO request = new ProductRequestDTO(
                "Producto Test", "SKU-902", "Descripcion", "General", BigDecimal.ZERO, 5, 1);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_withBlankName_throwsIllegalArgumentExceptionFromService() {
        ProductRequestDTO request = new ProductRequestDTO(
                "  ", "SKU-904", "Descripcion", "General", new BigDecimal("10.00"), 5, 1);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_withBlankSku_throwsIllegalArgumentExceptionFromService() {
        ProductRequestDTO request = new ProductRequestDTO(
                "Producto Test", " ", "Descripcion", "General", new BigDecimal("10.00"), 5, 1);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_withBlankCategory_throwsIllegalArgumentExceptionFromService() {
        ProductRequestDTO request = new ProductRequestDTO(
                "Producto Test", "SKU-905", "Descripcion", "  ", new BigDecimal("10.00"), 5, 1);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_withNegativeQuantity_throwsIllegalArgumentExceptionBeforeTouchingRepository() {
        UUID id = UUID.randomUUID();
        ProductRequestDTO request = new ProductRequestDTO(
                "Producto Test", "SKU-903", "Descripcion", "General", new BigDecimal("10.00"), -5, 1);

        assertThatThrownBy(() -> productService.updateProduct(id, request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void getAllProducts_withPagination_returnsPagedResult() {
        Pageable pageable = PageRequest.of(0, 10);
        Product entity = buildEntity(UUID.randomUUID(), "PAG-010", ProductStatus.ACTIVE);
        ProductResponseDTO responseDTO = buildResponseDTO(entity);
        Page<Product> page = new PageImpl<>(List.of(entity), pageable, 1);

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(productMapper.toResponseDTO(entity)).thenReturn(responseDTO);

        Page<ProductResponseDTO> result = productService.getAllProducts(pageable, null);

        assertThat(result.getContent()).containsExactly(responseDTO);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getAllProducts_withFilters_appliesPageableAndReturnsMappedPage() {
        Pageable pageable = PageRequest.of(1, 5);
        Product entity = buildEntity(UUID.randomUUID(), "PAG-011", ProductStatus.ACTIVE);
        ProductResponseDTO responseDTO = buildResponseDTO(entity);
        Page<Product> page = new PageImpl<>(List.of(entity), pageable, 6);
        ProductFilterDTO filter = new ProductFilterDTO(ProductStatus.ACTIVE, "General",
                new BigDecimal("1.00"), new BigDecimal("100.00"), "producto");

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(productMapper.toResponseDTO(entity)).thenReturn(responseDTO);

        Page<ProductResponseDTO> result = productService.getAllProducts(pageable, filter);

        assertThat(result.getContent()).containsExactly(responseDTO);
        assertThat(result.getTotalElements()).isEqualTo(6);
    }

    @Test
    void getProductsByCategory_returnsProductsInThatCategory() {
        Pageable pageable = PageRequest.of(0, 10);
        Product entity = buildEntity(UUID.randomUUID(), "CAT-012", ProductStatus.ACTIVE);
        entity.setCategory("Electronica");
        ProductResponseDTO responseDTO = buildResponseDTO(entity);
        Page<Product> page = new PageImpl<>(List.of(entity), pageable, 1);

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(productMapper.toResponseDTO(entity)).thenReturn(responseDTO);

        Page<ProductResponseDTO> result = productService.getProductsByCategory("Electronica", pageable);

        assertThat(result.getContent()).containsExactly(responseDTO);
    }

    @Test
    void searchProducts_returnsPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Product entity = buildEntity(UUID.randomUUID(), "SEA-013", ProductStatus.ACTIVE);
        ProductResponseDTO responseDTO = buildResponseDTO(entity);
        Page<Product> page = new PageImpl<>(List.of(entity), pageable, 1);

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(productMapper.toResponseDTO(entity)).thenReturn(responseDTO);

        Page<ProductResponseDTO> result = productService.searchProducts("producto", pageable);

        assertThat(result.getContent()).containsExactly(responseDTO);
    }

    @Test
    void searchProducts_withBlankQuery_doesNotThrowAndReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

        Page<ProductResponseDTO> result = productService.searchProducts(null, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getProductStats_calculatesTotalsCorrectly() {
        Product active1 = buildEntity(UUID.randomUUID(), "STA-014", ProductStatus.ACTIVE);
        active1.setPrice(new BigDecimal("10.00"));
        active1.setQuantity(5);
        Product active2 = buildEntity(UUID.randomUUID(), "STA-015", ProductStatus.ACTIVE);
        active2.setPrice(new BigDecimal("20.00"));
        active2.setQuantity(2);
        active2.setMinStock(10);

        when(productRepository.countByStatus(ProductStatus.ACTIVE)).thenReturn(2L);
        when(productRepository.countByStatus(ProductStatus.INACTIVE)).thenReturn(1L);
        when(productRepository.findByQuantityLessThanMinStockAndStatus(ProductStatus.ACTIVE))
                .thenReturn(List.of(active2));
        when(productRepository.findByStatus(ProductStatus.ACTIVE)).thenReturn(List.of(active1, active2));

        ProductStatsDTO stats = productService.getProductStats();

        assertThat(stats.totalProducts()).isEqualTo(3);
        assertThat(stats.activeProducts()).isEqualTo(2);
        assertThat(stats.inactiveProducts()).isEqualTo(1);
        assertThat(stats.belowMinStockProducts()).isEqualTo(1);
        assertThat(stats.totalInventoryValue()).isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    void createProduct_invalidName_throws() {
        ProductRequestDTO request = new ProductRequestDTO(
                "", "SKU-001", "Descripcion", "General", new BigDecimal("10.00"), 5, 1);
        Set<ConstraintViolation<ProductRequestDTO>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void createProduct_invalidPrice_throws() {
        ProductRequestDTO request = new ProductRequestDTO(
                "Producto Test", "SKU-001", "Descripcion", "General", BigDecimal.ZERO, 5, 1);
        Set<ConstraintViolation<ProductRequestDTO>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
    }

    @Test
    void createProduct_invalidCategory_throws() {
        ProductRequestDTO request = new ProductRequestDTO(
                "Producto Test", "SKU-001", "Descripcion", "", new BigDecimal("10.00"), 5, 1);
        Set<ConstraintViolation<ProductRequestDTO>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
    }

    @Test
    void updateProduct_invalidPrice_throws() {
        ProductRequestDTO request = new ProductRequestDTO(
                "Producto Test", "SKU-001", "Descripcion", "General", new BigDecimal("-1.00"), 5, 1);
        Set<ConstraintViolation<ProductRequestDTO>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
    }

    private ProductRequestDTO buildRequestDTO(String sku) {
        return new ProductRequestDTO("Producto " + sku, sku, "Descripcion", "General",
                new BigDecimal("10.00"), 5, 1);
    }

    private Product buildEntity(UUID id, String sku, ProductStatus status) {
        Product product = new Product();
        product.setId(id);
        product.setName("Producto " + sku);
        product.setSku(sku);
        product.setDescription("Descripcion");
        product.setCategory("General");
        product.setPrice(new BigDecimal("10.00"));
        product.setQuantity(5);
        product.setMinStock(1);
        product.setStatus(status);
        return product;
    }

    private ProductResponseDTO buildResponseDTO(Product product) {
        return new ProductResponseDTO(
                product.getId(), product.getName(), product.getSku(), product.getDescription(),
                product.getCategory(), product.getPrice(), product.getQuantity(), product.getMinStock(),
                product.getStatus(), LocalDateTime.now(), LocalDateTime.now(),
                product.getCreatedBy(), product.getVersion());
    }
}
