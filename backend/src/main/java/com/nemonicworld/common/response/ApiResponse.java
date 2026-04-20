package com.nemonicworld.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data, Map<String, String> errors) {

    // 성공
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    // 실패
    public static ApiResponse<Void> fail(String message, Map<String, String> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }

}
