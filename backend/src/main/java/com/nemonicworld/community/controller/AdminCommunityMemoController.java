package com.nemonicworld.community.controller;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.auth.service.AdminClientInfoResolver;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.community.dto.request.AdminCommunityMemoReviewRequest;
import com.nemonicworld.community.dto.response.AdminCommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoListResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoReportListResponse;
import com.nemonicworld.community.service.AdminCommunityMemoService;
import com.nemonicworld.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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
    private static final String REPORT_LIST_SUCCESS_MESSAGE = "관리자 커뮤니티 메모 신고 내역 조회 성공";
    private static final String HIDE_SUCCESS_MESSAGE = "커뮤니티 메모 숨김 처리 성공";
    private static final String RESTORE_SUCCESS_MESSAGE = "커뮤니티 메모 숨김 복구 성공";
    private static final String INVALID_HIDE_REASON_EXAMPLE = """
        {
          "success": false,
          "message": "커뮤니티 메모 숨김 사유가 올바르지 않습니다."
        }
        """;
    private static final String INVALID_RESTORE_REASON_EXAMPLE = """
        {
          "success": false,
          "message": "커뮤니티 메모 복구 사유가 올바르지 않습니다."
        }
        """;

    private final AdminCommunityMemoService adminCommunityMemoService;
    private final AdminClientInfoResolver adminClientInfoResolver;

    public AdminCommunityMemoController(AdminCommunityMemoService adminCommunityMemoService,
        AdminClientInfoResolver adminClientInfoResolver) {
        this.adminCommunityMemoService = adminCommunityMemoService;
        this.adminClientInfoResolver = adminClientInfoResolver;
    }

    /**
     * 삭제되지 않은 커뮤니티 메모를 관리자 운영 필터로 조회합니다.
     */
    @GetMapping
    @Operation(summary = "관리자 커뮤니티 메모 목록 조회", description = "관리자가 visible/hidden 커뮤니티 메모를 운영 필터와 페이징 조건으로 조회합니다.")
    @Parameter(name = "hidden", in = ParameterIn.QUERY, description = "숨김 여부 필터")
    @Parameter(name = "moderationStatus", in = ParameterIn.QUERY, description = "모더레이션 상태: pending, allowed, blocked")
    @Parameter(name = "sourceType", in = ParameterIn.QUERY, description = "출처 유형: DIRECT, GALLERY")
    @Parameter(name = "reported", in = ParameterIn.QUERY, description = "신고 여부 필터")
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
        @RequestParam(name = "reported", required = false) Boolean reported,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "page", required = false) String page,
        @RequestParam(name = "size", required = false) String size, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        AdminCommunityMemoListResponse response = adminCommunityMemoService.getCommunityMemos(adminPrincipal, hidden,
            moderationStatus, sourceType, reported, keyword, page, size, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }

    /**
     * 특정 커뮤니티 메모에 접수된 신고 내역을 최신 신고 순으로 조회합니다.
     */
    @GetMapping("/{memoId}/reports")
    @Operation(summary = "관리자 커뮤니티 메모 신고 내역 조회", description = "관리자가 특정 커뮤니티 메모에 접수된 신고 사유와 신고자 정보를 조회합니다.")
    @Parameter(name = "memoId", in = ParameterIn.PATH, required = true, description = "커뮤니티 메모 UUID")
    @Parameter(name = "reason", in = ParameterIn.QUERY, description = "신고 사유 enum 값")
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지 번호", example = "0")
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지 크기", example = "20")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 커뮤니티 메모 신고 내역 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "조회 조건 오류", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "신고 사유 오류", value = OpenApiErrorExamples.INVALID_COMMUNITY_MEMO_REPORT_REASON),
            @ExampleObject(name = "페이징 오류", value = OpenApiErrorExamples.BAD_REQUEST)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "커뮤니티 메모 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.COMMUNITY_MEMO_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<AdminCommunityMemoReportListResponse>> getCommunityMemoReports(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("memoId") String memoId,
        @RequestParam(name = "reason", required = false) String reason,
        @RequestParam(name = "page", required = false) String page,
        @RequestParam(name = "size", required = false) String size, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        AdminCommunityMemoReportListResponse response = adminCommunityMemoService
            .getCommunityMemoReports(adminPrincipal, memoId, reason, page, size, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(REPORT_LIST_SUCCESS_MESSAGE, response));
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
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("memoId") String memoId,
        HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        AdminCommunityMemoDetailResponse response = adminCommunityMemoService.getCommunityMemo(adminPrincipal, memoId,
            clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DETAIL_SUCCESS_MESSAGE, response));
    }

    /**
     * visible 커뮤니티 메모를 관리자 수동 숨김 처리합니다.
     */
    @PatchMapping("/{memoId}/hide")
    @Operation(summary = "관리자 커뮤니티 메모 숨김 처리", description = "관리자가 입력한 숨김 사유를 감사 로그에 남기고, 메모 상태에는 admin_hidden 사유를 기록합니다.")
    @Parameter(name = "memoId", in = ParameterIn.PATH, required = true, description = "숨김 처리할 커뮤니티 메모 UUID")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = AdminCommunityMemoReviewRequest.class), examples = @ExampleObject(value = """
        {
          "reason": "신고 내용 확인 결과 부적절한 이미지로 판단했습니다."
        }
        """)))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "커뮤니티 메모 숨김 처리 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청값 오류", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "숨김 사유 오류", value = INVALID_HIDE_REASON_EXAMPLE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "커뮤니티 메모 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.COMMUNITY_MEMO_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<AdminCommunityMemoDetailResponse>> hideCommunityMemo(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("memoId") String memoId,
        @RequestBody(required = false) AdminCommunityMemoReviewRequest request, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        AdminCommunityMemoDetailResponse response = adminCommunityMemoService.hideCommunityMemo(adminPrincipal, memoId,
            request, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(HIDE_SUCCESS_MESSAGE, response));
    }

    /**
     * hidden 커뮤니티 메모를 visible 상태로 복구합니다.
     */
    @PatchMapping("/{memoId}/restore")
    @Operation(summary = "관리자 커뮤니티 메모 숨김 복구", description = "관리자가 숨김 처리된 커뮤니티 메모를 다시 노출 상태로 복구합니다.")
    @Parameter(name = "memoId", in = ParameterIn.PATH, required = true, description = "복구할 커뮤니티 메모 UUID")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = AdminCommunityMemoReviewRequest.class), examples = @ExampleObject(value = """
        {
          "reason": "오신고로 확인되어 복구합니다."
        }
        """)))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "커뮤니티 메모 숨김 복구 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청값 오류", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "복구 사유 오류", value = INVALID_RESTORE_REASON_EXAMPLE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "커뮤니티 메모 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.COMMUNITY_MEMO_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<AdminCommunityMemoDetailResponse>> restoreCommunityMemo(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("memoId") String memoId,
        @RequestBody(required = false) AdminCommunityMemoReviewRequest request, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        AdminCommunityMemoDetailResponse response = adminCommunityMemoService.restoreCommunityMemo(adminPrincipal,
            memoId, request, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RESTORE_SUCCESS_MESSAGE, response));
    }
}
