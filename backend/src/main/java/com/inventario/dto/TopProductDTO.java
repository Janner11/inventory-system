package com.inventario.dto;

import java.util.UUID;

/** Producto con mas movimientos de stock en la ventana de tiempo consultada (por defecto, 30 dias). */
public record TopProductDTO(
        UUID productId,
        String sku,
        String name,
        long movementCount
) {
}
