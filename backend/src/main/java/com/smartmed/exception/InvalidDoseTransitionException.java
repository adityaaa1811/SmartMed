package com.smartmed.exception;

public class InvalidDoseTransitionException extends RuntimeException {
    public InvalidDoseTransitionException(String message) {
        super(message);
    }
}
