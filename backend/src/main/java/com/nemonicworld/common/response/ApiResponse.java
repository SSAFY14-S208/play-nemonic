package com.nemonicworld.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "공통 API 응답")
public record ApiResponse<T>(@Schema(description = "요청 성공 여부", example = "true") boolean success,

    @Schema(description = "응답 메시지", example = "요청 처리 성공") String message,

    @Schema(description = "응답 데이터. 실패 응답에서는 null이거나 생략됩니다.") T data,

    @Schema(description = "필드별 오류 상세 정보. 일반 오류 또는 성공 응답에서는 null이거나 생략됩니다.", example = """
        {
          "fieldName": "필드별 오류 메시지"
        }
        """, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Map<String, String> errors) {

    // 성공
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    // 실패
    public static ApiResponse<Void> fail(String message, Map<String, String> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }

}
