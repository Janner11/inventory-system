package com.inventario.service;

import com.inventario.dto.ProductRequestDTO;
import com.inventario.dto.ProductResponseDTO;
import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import com.inventario.exception.DuplicateSkuException;
import com.inventario.exception.ProductNotFoundException;
import com.inventario.mapper.ProductMapper;
import com.inventario.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    public List<ProductResponseDTO> getAllProducts() {
        return productRepository.findByStatus(ProductStatus.ACTIVE).stream()
                .map(productMapper::toResponseDTO)
                .toList();
    }

    public ProductResponseDTO getProductById(UUID id) {
        return productMapper.toResponseDTO(findProductOrThrow(id));
    }

    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO request) {
        String sku = request.sku().toUpperCase();
        ensureSkuIsAvailable(sku, null);

        Product product = productMapper.toEntity(request);
        product.setSku(sku);
        product.setStatus(ProductStatus.ACTIVE);

        return productMapper.toResponseDTO(productRepository.save(product));
    }

    @Transactional
    public ProductResponseDTO updateProduct(UUID id, ProductRequestDTO request) {
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

        return productMapper.toResponseDTO(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProductOrThrow(id);
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.save(product);
    }

    public List<ProductResponseDTO> getProductsBelowMinStock() {
        return productRepository.findByQuantityLessThanMinStockAndStatus(ProductStatus.ACTIVE).stream()
                .map(productMapper::toResponseDTO)
                .toList();
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
}
