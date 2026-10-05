package com.smartmed.dto.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorDetail(
        String code,
        String message,
        List<ErrorResponse.FieldErrorDetail> fieldErrors
) {
    public static ApiErrorDetail of(String code, String message) {
        return new ApiErrorDetail(code, message, null);
    }

    public static ApiErrorDetail of(String code, String message,
                                    List<ErrorResponse.FieldErrorDetail> fieldErrors) {
        return new ApiErrorDetail(code, message, fieldErrors);
    }
}
