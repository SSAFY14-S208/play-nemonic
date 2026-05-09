package com.nemonicworld.backoffice.setting.controller;

import com.nemonicworld.backoffice.setting.dto.request.SystemParameterBulkUpdateRequest;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterListResponse;
import com.nemonicworld.backoffice.setting.service.SystemParameterService;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice/system-parameters")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "System Parameters", description = "백오피스 시스템 파라미터 조회/수정 API")
public class SystemParameterController {

    private static final String LIST_SUCCESS_MESSAGE = "시스템 파라미터 목록 조회 성공";
    private static final String BULK_UPDATE_SUCCESS_MESSAGE = "시스템 파라미터 수정 성공";

    private final SystemParameterService systemParameterService;

    public SystemParameterController(SystemParameterService systemParameterService) {
        this.systemParameterService = systemParameterService;
    }

    @GetMapping
    @Operation(summary = "시스템 파라미터 목록 조회", description = "관리자가 백오피스 시스템 파라미터 목록을 조회합니다.")
    @Parameter(name = "keyword", in = ParameterIn.QUERY, description = "파라미터 키 검색어")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "시스템 파라미터 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED)))})
    public ResponseEntity<ApiResponse<SystemParameterListResponse>> getSystemParameters(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestParam(name = "keyword", required = false) String keyword) {
        SystemParameterListResponse response = systemParameterService.getSystemParameters(adminPrincipal, keyword);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }

    @PatchMapping
    @Operation(summary = "시스템 파라미터 일괄 수정", description = "관리자가 시스템 파라미터 여러 건을 한 트랜잭션으로 일괄 수정합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "시스템 파라미터 수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SYSTEM_PARAMETER_BULK_UPDATE_INVALID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED)))})
    public ResponseEntity<ApiResponse<SystemParameterListResponse>> bulkUpdateSystemParameters(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @Valid @RequestBody SystemParameterBulkUpdateRequest request) {
        SystemParameterListResponse response = systemParameterService.bulkUpdate(adminPrincipal, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(BULK_UPDATE_SUCCESS_MESSAGE, response));
    }
}
