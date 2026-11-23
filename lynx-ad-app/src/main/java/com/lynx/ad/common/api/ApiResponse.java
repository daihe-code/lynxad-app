package com.lynx.ad.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiResponse<T>(int code, String message, T data,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    public static <T> ApiResponse<T> failWithReason(int code, String message, String reason) {
        return new ApiResponse<>(code, message, null, reason);
    }
}
