package com.smartmed.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartmed.dto.error.ApiErrorDetail;
import com.smartmed.dto.error.ErrorResponse;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        ApiErrorDetail error
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> failureWithData(String code, String message, T data) {
        return new ApiResponse<>(false, null, data, ApiErrorDetail.of(code, message));
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, null, null, ApiErrorDetail.of(code, message));
    }

    public static ApiResponse<Void> failure(String code, String message,
                                            List<ErrorResponse.FieldErrorDetail> fieldErrors) {
        return new ApiResponse<>(false, null, null, ApiErrorDetail.of(code, message, fieldErrors));
    }
}
