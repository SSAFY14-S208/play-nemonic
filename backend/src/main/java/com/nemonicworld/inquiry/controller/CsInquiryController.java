package com.nemonicworld.inquiry.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.inquiry.dto.request.CsInquiryCreateRequest;
import com.nemonicworld.inquiry.dto.response.CsInquiryCreateResponse;
import com.nemonicworld.inquiry.service.CsInquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inquiries")
@Tag(name = "CS 문의", description = "익명 사용자 CS 문의 API")
public class CsInquiryController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String USER_AGENT_HEADER = HttpHeaders.USER_AGENT;
    private static final String REFERER_HEADER = HttpHeaders.REFERER;
    private static final String CREATE_SUCCESS_MESSAGE = "CS 문의 접수 성공";

    private final CsInquiryService csInquiryService;

    public CsInquiryController(CsInquiryService csInquiryService) {
        this.csInquiryService = csInquiryService;
    }

    @PostMapping
    @Operation(summary = "CS 문의 접수", description = "익명 사용자의 CS 문의를 접수합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true, description = "익명 사용자 UUID")
    @Parameter(name = USER_AGENT_HEADER, in = ParameterIn.HEADER, description = "클라이언트 User-Agent")
    @Parameter(name = REFERER_HEADER, in = ParameterIn.HEADER, description = "문의 작성 화면 URL")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "CS 문의 접수 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "요청 본문 오류", value = OpenApiErrorExamples.BAD_REQUEST)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "익명 사용자 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<CsInquiryCreateResponse>> createInquiry(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestHeader(value = USER_AGENT_HEADER, required = false) String userAgent,
        @RequestHeader(value = REFERER_HEADER, required = false) String referer,
        @Valid @RequestBody CsInquiryCreateRequest request) {
        CsInquiryCreateResponse response = csInquiryService.createInquiry(userUuid, userAgent, referer, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CREATE_SUCCESS_MESSAGE, response));
    }
}
