package com.nemonicworld.auth.controller;

import com.nemonicworld.auth.dto.request.AdminAccountCreateRequest;
import com.nemonicworld.auth.dto.response.AdminResponse;
import com.nemonicworld.auth.service.AdminAccountService;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/accounts")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Accounts", description = "백오피스 관리자 계정 API")
public class AdminAccountController {

    private static final String CREATE_SUCCESS_MESSAGE = "관리자 계정 생성 성공";

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @PostMapping
    @Operation(summary = "관리자 계정 생성", description = "슈퍼 관리자가 백오피스 운영자 계정을 생성합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "관리자 계정 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "슈퍼 관리자 권한 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_SUPER_ADMIN_REQUIRED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복 관리자 아이디", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_LOGIN_ID_DUPLICATED)))})
    public ResponseEntity<ApiResponse<AdminResponse>> createAdminAccount(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @Valid @RequestBody AdminAccountCreateRequest request) {
        AdminResponse response = adminAccountService.createAdminAccount(adminPrincipal, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CREATE_SUCCESS_MESSAGE, response));
    }
}
