package com.smartmed.exception;

import com.smartmed.dto.error.ErrorResponse;
import com.smartmed.exception.DuplicateCareRelationshipException;
import com.smartmed.exception.InvalidCareRelationshipRequestException;
import com.smartmed.exception.InvalidCareRelationshipStateException;
import com.smartmed.exception.InvalidNotificationPageException;
import com.smartmed.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidNotificationPageException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidNotificationPage(InvalidNotificationPageException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("INVALID_NOTIFICATION_PAGE", ex.getMessage()));
    }

    @ExceptionHandler(InvalidAdherenceHistoryPageException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidAdherenceHistoryPage(
            InvalidAdherenceHistoryPageException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("INVALID_ADHERENCE_HISTORY_PAGE", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateCareRelationshipException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateRelationship(DuplicateCareRelationshipException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("RELATIONSHIP_CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCareRelationshipStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleRelationshipState(InvalidCareRelationshipStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("INVALID_RELATIONSHIP_STATE", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCareRelationshipRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleRelationshipRequest(InvalidCareRelationshipRequestException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("INVALID_RELATIONSHIP_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("EMAIL_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("INVALID_CREDENTIALS", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("INVALID_REQUEST", "Request body is invalid"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("VALIDATION_ERROR", "Validation failed", details));
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidDateRange(InvalidDateRangeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("INVALID_DATE_RANGE", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                "INVALID_PARAMETER", "Invalid value for parameter: " + ex.getName()));
    }

    @ExceptionHandler(InvalidDoseTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidDoseTransition(InvalidDoseTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("INVALID_DOSE_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("ACCESS_DENIED", "You do not have permission to access this resource"));
    }

    @ExceptionHandler(JwtConfigurationException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwtConfiguration(JwtConfigurationException ex) {
        log.error("JWT configuration error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("CONFIGURATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(
                        "INTERNAL_ERROR",
                        "An unexpected error occurred. Please try again later."
                ));
    }

    private ErrorResponse.FieldErrorDetail toFieldError(FieldError fieldError) {
        return new ErrorResponse.FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
