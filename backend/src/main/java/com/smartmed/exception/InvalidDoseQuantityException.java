package com.smartmed.exception;

public class InvalidDoseQuantityException extends RuntimeException {
    public InvalidDoseQuantityException(String message) {
        super(message);
    }
}
