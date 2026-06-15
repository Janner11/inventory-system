package com.inventario.mapper;

import com.inventario.dto.StockMovementResponseDTO;
import com.inventario.entity.StockMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockMovementMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productSku", source = "product.sku")
    @Mapping(target = "productName", source = "product.name")
    StockMovementResponseDTO toResponseDTO(StockMovement stockMovement);
}
