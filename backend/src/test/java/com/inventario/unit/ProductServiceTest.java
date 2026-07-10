package com.inventario.unit;

import com.inventario.dto.ProductRequestDTO;
import com.inventario.dto.ProductResponseDTO;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    void getAllProducts_returnsActiveProducts() {
        Product entity = buildEntity(UUID.randomUUID(), "KEY-003", ProductStatus.ACTIVE);
        ProductResponseDTO responseDTO = buildResponseDTO(entity);

        when(productRepository.findByStatus(ProductStatus.ACTIVE)).thenReturn(List.of(entity));
        when(productMapper.toResponseDTO(entity)).thenReturn(responseDTO);

        List<ProductResponseDTO> result = productService.getAllProducts();

        assertThat(result).containsExactly(responseDTO);
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
                product.getStatus(), LocalDateTime.now(), LocalDateTime.now());
    }
}
