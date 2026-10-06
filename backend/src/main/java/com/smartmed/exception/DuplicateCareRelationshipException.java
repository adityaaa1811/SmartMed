package com.smartmed.exception;

public class DuplicateCareRelationshipException extends RuntimeException {
    public DuplicateCareRelationshipException(String message) {
        super(message);
    }
}
