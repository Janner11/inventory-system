package com.inventario.service;

import com.inventario.dto.ProductRequestDTO;
import com.inventario.dto.ProductResponseDTO;
import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import com.inventario.exception.DuplicateSkuException;
import com.inventario.exception.ProductNotFoundException;
import com.inventario.mapper.ProductMapper;
import com.inventario.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    public ProductResponseDTO createProduct(ProductRequestDTO dto) {
        String sku = dto.sku().toUpperCase();

        if (productRepository.findBySkuIgnoreCase(sku).isPresent()) {
            throw new DuplicateSkuException(sku);
        }

        Product product = productMapper.toEntity(dto);
        product.setSku(sku);
        product.setStatus(ProductStatus.ACTIVE);

        Product saved = productRepository.save(product);
        return productMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(UUID id) {
        Product product = findProductOrThrow(id);
        return productMapper.toResponseDTO(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(productMapper::toResponseDTO);
    }

    public ProductResponseDTO updateProduct(UUID id, ProductRequestDTO dto) {
        Product product = findProductOrThrow(id);

        String sku = dto.sku().toUpperCase();
        productRepository.findBySkuIgnoreCase(sku)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateSkuException(sku);
                });

        productMapper.updateEntityFromDto(dto, product);
        product.setSku(sku);

        Product saved = productRepository.save(product);
        return productMapper.toResponseDTO(saved);
    }

    public void deleteProduct(UUID id) {
        Product product = findProductOrThrow(id);
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getProductsBelowMinStock() {
        return productRepository.findByQuantityLessThanMinStockAndStatus(ProductStatus.ACTIVE).stream()
                .map(productMapper::toResponseDTO)
                .toList();
    }

    private Product findProductOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
