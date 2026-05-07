package com.nemonicworld.inquiry.controller;

import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.config.OpenApiConfig;
import com.nemonicworld.inquiry.dto.response.CsInquiryListResponse;
import com.nemonicworld.inquiry.service.CsInquiryService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/inquiries")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "관리자 문의", description = "백오피스 고객 문의 관리 API")
public class AdminInquiryController {

    private static final String LIST_SUCCESS_MESSAGE = "고객 문의 목록 조회 성공";

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
}
