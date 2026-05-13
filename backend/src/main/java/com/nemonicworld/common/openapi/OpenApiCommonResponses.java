package com.nemonicworld.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * 여러 컨트롤러에서 반복해서 참조하는 Swagger/OpenAPI 공통 실패 응답입니다.
 */
public final class OpenApiCommonResponses {

    public static final String ADMIN_UNAUTHORIZED = "AdminUnauthorized";
    public static final String SERVER_ERROR = "ServerError";

    public static final String ADMIN_UNAUTHORIZED_REF = "#/components/responses/" + ADMIN_UNAUTHORIZED;
    public static final String SERVER_ERROR_REF = "#/components/responses/" + SERVER_ERROR;

    private static final String APPLICATION_JSON = "application/json";
    private static final String DEFAULT_EXAMPLE = "default";

    private OpenApiCommonResponses() {
    }

    public static Components register(Components components) {
        return components
            .addResponses(ADMIN_UNAUTHORIZED, errorResponse("관리자 인증 필요", OpenApiErrorExamples.ADMIN_UNAUTHORIZED))
            .addResponses(SERVER_ERROR, errorResponse("서버 오류", OpenApiErrorExamples.SERVER_ERROR));
    }

    private static ApiResponse errorResponse(String description, String example) {
        return new ApiResponse().description(description).content(new Content().addMediaType(APPLICATION_JSON,
            new MediaType().addExamples(DEFAULT_EXAMPLE, new Example().value(example))));
    }
}
