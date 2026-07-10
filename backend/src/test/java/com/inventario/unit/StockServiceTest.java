package com.inventario.unit;

import com.inventario.dto.StockAdjustmentRequestDTO;
import com.inventario.dto.StockMovementRequestDTO;
import com.inventario.dto.StockMovementResponseDTO;
import com.inventario.entity.MovementType;
import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import com.inventario.entity.StockMovement;
import com.inventario.exception.InsufficientStockException;
import com.inventario.exception.ProductInactiveException;
import com.inventario.exception.ProductNotFoundException;
import com.inventario.mapper.StockMovementMapper;
import com.inventario.repository.ProductRepository;
import com.inventario.repository.StockMovementRepository;
import com.inventario.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private StockMovementMapper stockMovementMapper;

    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockService = new StockService(productRepository, stockMovementRepository, stockMovementMapper);
    }

    @Test
    void registerEntry_withValidData_incrementsProductQuantity() {
        UUID productId = UUID.randomUUID();
        Product product = buildProduct(productId, "LAP-001", 10, 2, ProductStatus.ACTIVE);
        StockMovementRequestDTO request = new StockMovementRequestDTO(productId, 5, "Reabastecimiento", null, "admin");

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementMapper.toResponseDTO(any(StockMovement.class))).thenReturn(mockResponse());

        stockService.registerEntry(request);

        assertThat(product.getQuantity()).isEqualTo(15);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        StockMovement saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(MovementType.ENTRY);
        assertThat(saved.getPreviousQuantity()).isEqualTo(10);
        assertThat(saved.getNewQuantity()).isEqualTo(15);
        assertThat(saved.getQuantity()).isEqualTo(5);
    }

    @Test
    void registerEntry_withNonExistingProduct_throwsProductNotFoundException() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        StockMovementRequestDTO request = new StockMovementRequestDTO(productId, 5, "Reabastecimiento", null, "admin");

        assertThatThrownBy(() -> stockService.registerEntry(request))
                .isInstanceOf(ProductNotFoundException.class);

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void registerEntry_onInactiveProduct_throwsProductInactiveException() {
        UUID productId = UUID.randomUUID();
        Product product = buildProduct(productId, "LAP-001", 10, 2, ProductStatus.INACTIVE);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        StockMovementRequestDTO request = new StockMovementRequestDTO(productId, 5, "Reabastecimiento", null, "admin");

        assertThatThrownBy(() -> stockService.registerEntry(request))
                .isInstanceOf(ProductInactiveException.class);

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void registerExit_withSufficientStock_decrementsQuantity() {
        UUID productId = UUID.randomUUID();
        Product product = buildProduct(productId, "MOU-002", 10, 2, ProductStatus.ACTIVE);
        StockMovementRequestDTO request = new StockMovementRequestDTO(productId, 4, "Venta", null, "admin");

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementMapper.toResponseDTO(any(StockMovement.class))).thenReturn(mockResponse());

        stockService.registerExit(request);

        assertThat(product.getQuantity()).isEqualTo(6);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        StockMovement saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(MovementType.EXIT);
        assertThat(saved.getPreviousQuantity()).isEqualTo(10);
        assertThat(saved.getNewQuantity()).isEqualTo(6);
        assertThat(saved.getQuantity()).isEqualTo(-4);
    }

    @Test
    void registerExit_withInsufficientStock_throwsInsufficientStockException() {
        UUID productId = UUID.randomUUID();
        Product product = buildProduct(productId, "MOU-002", 3, 2, ProductStatus.ACTIVE);
        StockMovementRequestDTO request = new StockMovementRequestDTO(productId, 5, "Venta", null, "admin");

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> stockService.registerExit(request))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(product.getQuantity()).isEqualTo(3);
        verify(productRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void adjustStock_updatesToAbsoluteQuantity() {
        UUID productId = UUID.randomUUID();
        Product product = buildProduct(productId, "KEY-003", 10, 2, ProductStatus.ACTIVE);
        StockAdjustmentRequestDTO request = new StockAdjustmentRequestDTO(productId, 7, "Conteo fisico", null, "admin");

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementMapper.toResponseDTO(any(StockMovement.class))).thenReturn(mockResponse());

        stockService.adjustStock(request);

        assertThat(product.getQuantity()).isEqualTo(7);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        StockMovement saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(saved.getPreviousQuantity()).isEqualTo(10);
        assertThat(saved.getNewQuantity()).isEqualTo(7);
        assertThat(saved.getQuantity()).isEqualTo(-3);
    }

    @Test
    void registerExit_whenResultBelowMinStock_keepsProductBelowMinStock() {
        UUID productId = UUID.randomUUID();
        Product product = buildProduct(productId, "MON-004", 5, 4, ProductStatus.ACTIVE);
        StockMovementRequestDTO request = new StockMovementRequestDTO(productId, 2, "Venta", null, "admin");

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementMapper.toResponseDTO(any(StockMovement.class))).thenReturn(mockResponse());

        StockMovementResponseDTO result = stockService.registerExit(request);

        assertThat(product.getQuantity()).isEqualTo(3);
        assertThat(product.getQuantity()).isLessThan(product.getMinStock());
        assertThat(result).isNotNull();
    }

    private Product buildProduct(UUID id, String sku, int quantity, int minStock, ProductStatus status) {
        Product product = new Product();
        product.setId(id);
        product.setName("Producto " + sku);
        product.setSku(sku);
        product.setDescription("Descripcion");
        product.setCategory("General");
        product.setPrice(new BigDecimal("10.00"));
        product.setQuantity(quantity);
        product.setMinStock(minStock);
        product.setStatus(status);
        return product;
    }

    private StockMovementResponseDTO mockResponse() {
        return new StockMovementResponseDTO(UUID.randomUUID(), UUID.randomUUID(), "SKU", "Producto",
                MovementType.ENTRY, 0, 0, 0, null, null, "admin", null);
    }
}
