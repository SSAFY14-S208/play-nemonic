package com.nemonicworld.admin.controller;

import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.admin.dto.request.AdminCreateRequest;
import com.nemonicworld.admin.dto.request.AdminPasswordChangeRequest;
import com.nemonicworld.admin.dto.response.AdminResponse;
import com.nemonicworld.admin.service.AdminService;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.auth.service.AdminClientInfoResolver;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admins")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = OpenApiTags.ADMIN, description = OpenApiTags.ADMIN_DESCRIPTION)
public class AdminController {

    private static final String CREATE_SUCCESS_MESSAGE = "관리자 계정 생성 성공";

    private static final String DELETE_SUCCESS_MESSAGE = "관리자 계정 삭제 성공";

    private static final String LIST_SUCCESS_MESSAGE = "관리자 계정 목록 조회 성공";

    private static final String DETAIL_SUCCESS_MESSAGE = "관리자 계정 상세 조회 성공";

    private static final String PASSWORD_CHANGE_SUCCESS_MESSAGE = "관리자 비밀번호 변경 성공";

    private final AdminService adminService;
    private final AdminClientInfoResolver adminClientInfoResolver;

    public AdminController(AdminService adminService, AdminClientInfoResolver adminClientInfoResolver) {
        this.adminService = adminService;
        this.adminClientInfoResolver = adminClientInfoResolver;
    }

    @PostMapping
    @Operation(summary = "관리자 계정 생성", description = "슈퍼 관리자가 백오피스 운영자 계정을 생성합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "관리자 계정 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "슈퍼 관리자 권한 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_SUPER_ADMIN_REQUIRED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복 관리자 아이디", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_LOGIN_ID_DUPLICATED)))})
    public ResponseEntity<ApiResponse<AdminResponse>> createAdmin(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @Valid @RequestBody AdminCreateRequest request,
        HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        AdminResponse response = adminService.createAdmin(adminPrincipal, request, clientInfo);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CREATE_SUCCESS_MESSAGE, response));
    }

    @GetMapping
    @Operation(summary = "관리자 계정 목록 조회", description = "슈퍼 관리자가 활성 관리자 계정 목록을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 계정 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "슈퍼 관리자 권한 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_SUPER_ADMIN_REQUIRED)))})
    public ResponseEntity<ApiResponse<List<AdminResponse>>> findAdmins(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        List<AdminResponse> response = adminService.findAdmins(adminPrincipal);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/{adminId}")
    @Operation(summary = "관리자 계정 상세 조회", description = "슈퍼 관리자가 관리자 계정 상세 정보를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 계정 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "슈퍼 관리자 권한 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_SUPER_ADMIN_REQUIRED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관리자 계정 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_ACCOUNT_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<AdminResponse>> findAdmin(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @PathVariable("adminId") Long adminId) {
        AdminResponse response = adminService.findAdmin(adminPrincipal, adminId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DETAIL_SUCCESS_MESSAGE, response));
    }

    @PatchMapping("/{adminId}")
    @Operation(summary = "관리자 비밀번호 변경", description = "슈퍼 관리자가 일반 관리자 계정의 비밀번호를 변경합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 비밀번호 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 비밀번호 변경 권한 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "superAdminRequired", value = OpenApiErrorExamples.ADMIN_SUPER_ADMIN_REQUIRED),
            @ExampleObject(name = "super", value = OpenApiErrorExamples.ADMIN_SUPER_DELETE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관리자 계정 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_ACCOUNT_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<Void>> changeAdminPassword(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @PathVariable("adminId") Long adminId, @Valid @RequestBody AdminPasswordChangeRequest request) {
        adminService.changeAdminPassword(adminPrincipal, adminId, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(PASSWORD_CHANGE_SUCCESS_MESSAGE, null));
    }

    @DeleteMapping("/{adminId}")
    @Operation(summary = "관리자 계정 삭제", description = "슈퍼 관리자가 일반 관리자 계정을 삭제합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 계정 삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 계정 삭제 권한 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "superAdminRequired", value = OpenApiErrorExamples.ADMIN_SUPER_ADMIN_REQUIRED),
            @ExampleObject(name = "selfDeleteForbidden", value = OpenApiErrorExamples.ADMIN_SELF_DELETE_FORBIDDEN),
            @ExampleObject(name = "super", value = OpenApiErrorExamples.ADMIN_SUPER_DELETE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관리자 계정 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_ACCOUNT_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<Void>> deleteAdmin(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @PathVariable("adminId") Long adminId, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        adminService.deleteAdmin(adminPrincipal, adminId, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DELETE_SUCCESS_MESSAGE, null));
    }
}
