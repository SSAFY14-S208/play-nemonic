package com.nemonicworld.community.controller;

import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoListResponse;
import com.nemonicworld.community.service.AdminCommunityMemoService;
import com.nemonicworld.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 커뮤니티 메모 검토와 숨김/복구 요청을 받는 컨트롤러입니다.
 */
@RestController
@RequestMapping("/admin/community/memos")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Community", description = "관리자 커뮤니티 메모 검토 API")
public class AdminCommunityMemoController {

    private static final String LIST_SUCCESS_MESSAGE = "관리자 커뮤니티 메모 목록 조회 성공";
    private static final String DETAIL_SUCCESS_MESSAGE = "관리자 커뮤니티 메모 상세 조회 성공";
    private static final String HIDE_SUCCESS_MESSAGE = "커뮤니티 메모 숨김 처리 성공";
    private static final String RESTORE_SUCCESS_MESSAGE = "커뮤니티 메모 숨김 복구 성공";

    private final AdminCommunityMemoService adminCommunityMemoService;

    public AdminCommunityMemoController(AdminCommunityMemoService adminCommunityMemoService) {
        this.adminCommunityMemoService = adminCommunityMemoService;
    }

    /**
     * 삭제되지 않은 커뮤니티 메모를 관리자 운영 필터로 조회합니다.
     */
    @GetMapping
    @Operation(summary = "관리자 커뮤니티 메모 목록 조회", description = "관리자가 visible/hidden 커뮤니티 메모를 운영 필터와 페이징 조건으로 조회합니다.")
    @Parameter(name = "hidden", in = ParameterIn.QUERY, description = "숨김 여부 필터")
    @Parameter(name = "moderationStatus", in = ParameterIn.QUERY, description = "모더레이션 상태: pending, allowed, blocked")
    @Parameter(name = "sourceType", in = ParameterIn.QUERY, description = "출처 유형: DIRECT, GALLERY")
    @Parameter(name = "keyword", in = ParameterIn.QUERY, description = "작성자 닉네임 또는 OCR 텍스트 검색어")
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지 번호", example = "0")
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지 크기", example = "20")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 커뮤니티 메모 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "조회 조건 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED)))})
    public ResponseEntity<ApiResponse<AdminCommunityMemoListResponse>> getCommunityMemos(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestParam(name = "hidden", required = false) Boolean hidden,
        @RequestParam(name = "moderationStatus", required = false) String moderationStatus,
        @RequestParam(name = "sourceType", required = false) String sourceType,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "page", required = false) String page,
        @RequestParam(name = "size", required = false) String size) {
        AdminCommunityMemoListResponse response = adminCommunityMemoService.getCommunityMemos(adminPrincipal, hidden,
            moderationStatus, sourceType, keyword, page, size);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }

    /**
     * 삭제되지 않은 커뮤니티 메모를 hidden 여부와 관계없이 단건 조회합니다.
     */
    @GetMapping("/{memoId}")
    @Operation(summary = "관리자 커뮤니티 메모 상세 조회", description = "관리자가 hidden 메모를 포함해 커뮤니티 메모 운영 상세 정보를 조회합니다.")
    @Parameter(name = "memoId", in = ParameterIn.PATH, required = true, description = "커뮤니티 메모 UUID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 커뮤니티 메모 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "메모 UUID 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "커뮤니티 메모 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.COMMUNITY_MEMO_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<AdminCommunityMemoDetailResponse>> getCommunityMemo(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("memoId") String memoId) {
        AdminCommunityMemoDetailResponse response = adminCommunityMemoService.getCommunityMemo(adminPrincipal, memoId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DETAIL_SUCCESS_MESSAGE, response));
    }

    /**
     * visible 커뮤니티 메모를 관리자 수동 숨김 처리합니다.
     */
    @PatchMapping("/{memoId}/hide")
    @Operation(summary = "관리자 커뮤니티 메모 숨김 처리", description = "관리자가 커뮤니티 메모를 숨김 처리하면 서버가 admin_hidden 사유를 고정 기록합니다.")
    @Parameter(name = "memoId", in = ParameterIn.PATH, required = true, description = "숨김 처리할 커뮤니티 메모 UUID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "커뮤니티 메모 숨김 처리 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "메모 UUID 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "커뮤니티 메모 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.COMMUNITY_MEMO_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<AdminCommunityMemoDetailResponse>> hideCommunityMemo(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("memoId") String memoId) {
        AdminCommunityMemoDetailResponse response = adminCommunityMemoService.hideCommunityMemo(adminPrincipal, memoId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(HIDE_SUCCESS_MESSAGE, response));
    }

    /**
     * hidden 커뮤니티 메모를 visible 상태로 복구합니다.
     */
    @PatchMapping("/{memoId}/restore")
    @Operation(summary = "관리자 커뮤니티 메모 숨김 복구", description = "관리자가 숨김 처리된 커뮤니티 메모를 다시 노출 상태로 복구합니다.")
    @Parameter(name = "memoId", in = ParameterIn.PATH, required = true, description = "복구할 커뮤니티 메모 UUID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "커뮤니티 메모 숨김 복구 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "메모 UUID 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "커뮤니티 메모 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.COMMUNITY_MEMO_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<AdminCommunityMemoDetailResponse>> restoreCommunityMemo(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("memoId") String memoId) {
        AdminCommunityMemoDetailResponse response = adminCommunityMemoService.restoreCommunityMemo(adminPrincipal,
            memoId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RESTORE_SUCCESS_MESSAGE, response));
    }
}
