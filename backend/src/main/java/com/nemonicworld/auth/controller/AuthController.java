package com.nemonicworld.auth.controller;

import com.nemonicworld.auth.dto.request.LoginRequest;
import com.nemonicworld.auth.dto.request.LogoutRequest;
import com.nemonicworld.auth.dto.request.TokenRefreshRequest;
import com.nemonicworld.auth.dto.response.LoginResponse;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.auth.service.AdminClientInfoResolver;
import com.nemonicworld.auth.service.AuthService;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ADMIN_LOGIN_SUCCESS_MESSAGE = "관리자 로그인 성공";
    private static final String ADMIN_TOKEN_REFRESH_SUCCESS_MESSAGE = "관리자 토큰 재발급 성공";
    private static final String ADMIN_LOGOUT_SUCCESS_MESSAGE = "관리자 로그아웃 성공";

    private final AuthService authService;
    private final AdminClientInfoResolver adminClientInfoResolver;

    public AuthController(AuthService authService, AdminClientInfoResolver adminClientInfoResolver) {
        this.authService = authService;
        this.adminClientInfoResolver = adminClientInfoResolver;
    }

    @PostMapping("/login")
    @Operation(summary = "관리자 로그인", description = "백오피스 운영자 계정으로 로그인하고 관리자 JWT와 리프레시 토큰을 발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 실패", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_AUTHENTICATION_FAILED)))})
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LoginResponse response = authService.login(request, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ADMIN_LOGIN_SUCCESS_MESSAGE, response));
    }

    @PostMapping("/reissue")
    @Operation(summary = "관리자 토큰 재발급", description = "Redis에 저장된 리프레시 토큰을 검증하고 새 access/refresh 토큰을 발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 토큰 재발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "리프레시 토큰 인증 실패", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED)))})
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        LoginResponse response = authService.refreshToken(request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ADMIN_TOKEN_REFRESH_SUCCESS_MESSAGE, response));
    }

    @PostMapping("/logout")
    @Operation(summary = "관리자 로그아웃", description = "관리자 리프레시 토큰을 폐기하고 현재 access token을 Redis 블랙리스트에 등록합니다.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 로그아웃 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED)))})
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @Valid @RequestBody LogoutRequest request, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        authService.logout(adminPrincipal, request, extractBearerToken(servletRequest), clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ADMIN_LOGOUT_SUCCESS_MESSAGE, null));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return "";
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
