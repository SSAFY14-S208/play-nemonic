package com.nemonicworld.inquiry.controller;

import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.config.OpenApiConfig;
import com.nemonicworld.inquiry.dto.request.CsInquiryReplyRequest;
import com.nemonicworld.inquiry.dto.response.CsInquiryDetailResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryListResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryReplyResponse;
import com.nemonicworld.inquiry.service.CsInquiryService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/inquiries")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "관리자 문의", description = "백오피스 고객 문의 관리 API")
public class AdminInquiryController {

    private static final String DETAIL_SUCCESS_MESSAGE = "고객 문의 상세 조회 성공";
    private static final String LIST_SUCCESS_MESSAGE = "고객 문의 목록 조회 성공";
    private static final String REPLY_SUCCESS_MESSAGE = "고객 문의 이메일 회신 성공";
    private static final String INQUIRY_NOT_FOUND_EXAMPLE = """
        {
          "success": false,
          "message": "고객 문의를 찾을 수 없습니다."
        }
        """;

    private final CsInquiryService csInquiryService;

    public AdminInquiryController(CsInquiryService csInquiryService) {
        this.csInquiryService = csInquiryService;
    }

    @GetMapping
    @Operation(summary = "고객 문의 목록 조회", description = "관리자가 고객 문의 목록을 필터와 페이지 조건으로 조회합니다.")
    @Parameter(name = "status", in = ParameterIn.QUERY, description = "문의 상태: new, in_progress, resolved, closed")
    @Parameter(name = "type", in = ParameterIn.QUERY, description = "문의 유형")
    @Parameter(name = "keyword", in = ParameterIn.QUERY, description = "제목, 내용, 이메일 검색어")
    @Parameter(name = "userUuid", in = ParameterIn.QUERY, description = "익명 사용자 UUID")
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지 번호", example = "0")
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지 크기", example = "20")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "고객 문의 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 파라미터 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED)))})
    public ResponseEntity<ApiResponse<CsInquiryListResponse>> getInquiries(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "type", required = false) String type,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "userUuid", required = false) String userUuid,
        @RequestParam(name = "page", required = false) String page,
        @RequestParam(name = "size", required = false) String size) {
        CsInquiryListResponse response = csInquiryService.getInquiries(adminPrincipal, status, type, keyword, userUuid,
            page, size);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/{inquiryId}")
    @Operation(summary = "고객 문의 상세 조회", description = "관리자가 고객 문의 상세 정보를 조회합니다.")
    @Parameter(name = "inquiryId", in = ParameterIn.PATH, required = true, description = "조회할 문의 ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "고객 문의 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "문의 ID 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "고객 문의 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INQUIRY_NOT_FOUND_EXAMPLE)))})
    public ResponseEntity<ApiResponse<CsInquiryDetailResponse>> getInquiry(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("inquiryId") String inquiryId) {
        CsInquiryDetailResponse response = csInquiryService.getInquiry(adminPrincipal, inquiryId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DETAIL_SUCCESS_MESSAGE, response));
    }

    @PostMapping("/{inquiryId}/reply")
    @Operation(summary = "고객 문의 이메일 회신", description = "관리자가 고객 문의에 이메일로 회신하고 문의를 완료 처리합니다.")
    @Parameter(name = "inquiryId", in = ParameterIn.PATH, required = true, description = "회신할 문의 ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "고객 문의 이메일 회신 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "고객 문의 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INQUIRY_NOT_FOUND_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "이메일 발송 실패", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<CsInquiryReplyResponse>> replyInquiry(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("inquiryId") String inquiryId,
        @Valid @RequestBody CsInquiryReplyRequest request) {
        CsInquiryReplyResponse response = csInquiryService.replyInquiry(adminPrincipal, inquiryId, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(REPLY_SUCCESS_MESSAGE, response));
    }
}
