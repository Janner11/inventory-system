package com.inventario.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockMovementMapper stockMovementMapper;

    public StockService(ProductRepository productRepository,
                         StockMovementRepository stockMovementRepository,
                         StockMovementMapper stockMovementMapper) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.stockMovementMapper = stockMovementMapper;
    }

    public StockMovementResponseDTO registerEntry(StockMovementRequestDTO request) {
        Product product = findActiveProductOrThrow(request.productId());

        int previousQuantity = product.getQuantity();
        int newQuantity = previousQuantity + request.quantity();
        product.setQuantity(newQuantity);
        productRepository.save(product);

        StockMovement movement = buildMovement(product, MovementType.ENTRY, previousQuantity, newQuantity,
                request.quantity(), request.reason(), request.observations(), request.performedBy());

        checkLowStockAlert(product);

        return stockMovementMapper.toResponseDTO(stockMovementRepository.save(movement));
    }

    public StockMovementResponseDTO registerExit(StockMovementRequestDTO request) {
        Product product = findActiveProductOrThrow(request.productId());

        int previousQuantity = product.getQuantity();
        if (request.quantity() > previousQuantity) {
            throw new InsufficientStockException(product.getSku(), previousQuantity, request.quantity());
        }

        int newQuantity = previousQuantity - request.quantity();
        product.setQuantity(newQuantity);
        productRepository.save(product);

        StockMovement movement = buildMovement(product, MovementType.EXIT, previousQuantity, newQuantity,
                -request.quantity(), request.reason(), request.observations(), request.performedBy());

        checkLowStockAlert(product);

        return stockMovementMapper.toResponseDTO(stockMovementRepository.save(movement));
    }

    public StockMovementResponseDTO adjustStock(StockAdjustmentRequestDTO request) {
        Product product = findActiveProductOrThrow(request.productId());

        int previousQuantity = product.getQuantity();
        int newQuantity = request.newQuantity();
        product.setQuantity(newQuantity);
        productRepository.save(product);

        StockMovement movement = buildMovement(product, MovementType.ADJUSTMENT, previousQuantity, newQuantity,
                newQuantity - previousQuantity, request.reason(), request.observations(), request.performedBy());

        checkLowStockAlert(product);

        return stockMovementMapper.toResponseDTO(stockMovementRepository.save(movement));
    }

    private Product findActiveProductOrThrow(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ProductInactiveException(product.getSku());
        }

        return product;
    }

    private StockMovement buildMovement(Product product, MovementType type, int previousQuantity, int newQuantity,
                                          int quantity, String reason, String observations, String performedBy) {
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(type);
        movement.setPreviousQuantity(previousQuantity);
        movement.setNewQuantity(newQuantity);
        movement.setQuantity(quantity);
        movement.setReason(reason);
        movement.setObservations(observations);
        movement.setPerformedBy(performedBy);
        return movement;
    }

    private void checkLowStockAlert(Product product) {
        if (product.getQuantity() < product.getMinStock()) {
            log.warn("Alerta de stock bajo: producto {} ({}) quedo con {} unidades, minimo {}",
                    product.getSku(), product.getName(), product.getQuantity(), product.getMinStock());
        }
    }
}
