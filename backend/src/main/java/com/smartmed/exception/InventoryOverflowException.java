package com.smartmed.exception;

public class InventoryOverflowException extends RuntimeException {
    public InventoryOverflowException(String message) {
        super(message);
    }
}
