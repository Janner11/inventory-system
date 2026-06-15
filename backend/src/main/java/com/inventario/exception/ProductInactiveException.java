package com.inventario.exception;

public class ProductInactiveException extends RuntimeException {

    public ProductInactiveException(String sku) {
        super("El producto " + sku + " esta inactivo y no admite movimientos de stock");
    }
}
