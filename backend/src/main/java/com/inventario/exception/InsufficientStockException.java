package com.inventario.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String sku, int available, int requested) {
        super("Stock insuficiente para el producto " + sku + ": disponible " + available + ", solicitado " + requested);
    }
}
