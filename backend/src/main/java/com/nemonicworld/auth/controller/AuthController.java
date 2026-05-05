package com.nemonicworld.auth.controller;

import com.nemonicworld.auth.dto.request.AdminLoginRequest;
import com.nemonicworld.auth.dto.response.AdminLoginResponse;
import com.nemonicworld.auth.dto.response.AdminResponse;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.auth.service.AdminClientInfoResolver;
import com.nemonicworld.auth.service.AuthService;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private static final String ADMIN_LOGIN_SUCCESS_MESSAGE = "관리자 로그인 성공";
    private static final String ADMIN_PROFILE_SUCCESS_MESSAGE = "관리자 정보 조회 성공";
    private static final String ADMIN_LOGOUT_SUCCESS_MESSAGE = "관리자 로그아웃 성공";

    private final AuthService authService;
    private final AdminClientInfoResolver adminClientInfoResolver;

    public AuthController(AuthService authService, AdminClientInfoResolver adminClientInfoResolver) {
        this.authService = authService;
        this.adminClientInfoResolver = adminClientInfoResolver;
    }

    @PostMapping("/admin/login")
    @Operation(summary = "관리자 로그인", description = "백오피스 운영자 계정으로 로그인하고 관리자 JWT를 발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 실패", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_AUTHENTICATION_FAILED)))})
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(@Valid @RequestBody AdminLoginRequest request,
        HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        AdminLoginResponse response = authService.login(request, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ADMIN_LOGIN_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/admin/me")
    @Operation(summary = "현재 관리자 정보 조회", description = "관리자 JWT로 현재 로그인한 운영자 계정 정보를 조회합니다.")
    @Parameter(name = HttpHeaders.AUTHORIZATION, in = ParameterIn.HEADER, required = true, description = "Bearer JWT")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 정보 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED)))})
    public ResponseEntity<ApiResponse<AdminResponse>> getCurrentAdmin(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        AdminResponse response = authService.getCurrentAdmin(adminPrincipal);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ADMIN_PROFILE_SUCCESS_MESSAGE, response));
    }

    @PostMapping("/admin/logout")
    @Operation(summary = "관리자 로그아웃", description = "관리자 로그아웃 이벤트를 감사 로그로 남깁니다.")
    @Parameter(name = HttpHeaders.AUTHORIZATION, in = ParameterIn.HEADER, required = true, description = "Bearer JWT")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 로그아웃 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED)))})
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        authService.logout(adminPrincipal, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ADMIN_LOGOUT_SUCCESS_MESSAGE, null));
    }
}
