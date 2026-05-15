package com.nemonicworld.backoffice.infinitecanvas.controller;

import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasListResponse;
import com.nemonicworld.backoffice.infinitecanvas.service.BackofficeInfiniteCanvasService;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiCommonResponses;
import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice/infinite-canvas/canvases")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = OpenApiTags.BACKOFFICE_INFINITE_CANVAS, description = OpenApiTags.BACKOFFICE_INFINITE_CANVAS_DESCRIPTION)
public class BackofficeInfiniteCanvasController {

    private static final String LIST_SUCCESS_MESSAGE = "활성 무한 캔버스 목록 조회 성공";
    private static final String STATUS_PARAMETER_DESCRIPTION = "캔버스 상태 필터: ACTIVE. 생략 시 ACTIVE. CLOSED는 허용되지 않습니다.";

    private final BackofficeInfiniteCanvasService backofficeInfiniteCanvasService;

    public BackofficeInfiniteCanvasController(BackofficeInfiniteCanvasService backofficeInfiniteCanvasService) {
        this.backofficeInfiniteCanvasService = backofficeInfiniteCanvasService;
    }

    @GetMapping
    @Operation(summary = "활성 무한 캔버스 목록 조회", description = "관리자가 백오피스에서 활성 무한 캔버스를 조회합니다.")
    @Parameter(name = "status", in = ParameterIn.QUERY, description = STATUS_PARAMETER_DESCRIPTION, example = "ACTIVE")
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지 번호 (0-based)", example = "0")
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지 크기. 기본 20, 최대 100으로 클램프", example = "20")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "활성 무한 캔버스 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF)})
    public ResponseEntity<ApiResponse<BackofficeInfiniteCanvasListResponse>> getActiveCanvases(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "page", required = false) String page,
        @RequestParam(name = "size", required = false) String size) {
        BackofficeInfiniteCanvasListResponse response = backofficeInfiniteCanvasService
            .getActiveCanvases(adminPrincipal, status, page, size);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }
}
