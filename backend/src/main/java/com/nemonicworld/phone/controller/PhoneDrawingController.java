package com.nemonicworld.phone.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.phone.dto.request.PhoneDrawingSaveRequest;
import com.nemonicworld.phone.dto.response.PhoneDrawingSaveResponse;
import com.nemonicworld.phone.service.PhoneDrawingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gallery/drawings")
@Tag(name = "Phone Drawing", description = "휴대폰 그림 갤러리 저장 API")
public class PhoneDrawingController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String SAVE_SUCCESS_MESSAGE = "휴대폰 그림 갤러리 저장 성공";

    private final PhoneDrawingService phoneDrawingService;

    public PhoneDrawingController(PhoneDrawingService phoneDrawingService) {
        this.phoneDrawingService = phoneDrawingService;
    }

    @PostMapping
    @Operation(summary = "휴대폰 그림 갤러리 저장", description = "휴대폰 모달에서 그린 이미지를 phone 산출물로 저장하고 내 갤러리에 추가합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true, description = "익명 사용자 UUID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "휴대폰 그림 갤러리 저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "파일 접근 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_ACCESS_DENIED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 또는 파일 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "파일 없음", value = OpenApiErrorExamples.FILE_UPLOAD_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "파일 업로드 상태 충돌", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_UPLOAD_STATUS_CONFLICT)))})
    public ResponseEntity<ApiResponse<PhoneDrawingSaveResponse>> savePhoneDrawing(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestBody(required = false) PhoneDrawingSaveRequest request) {
        PhoneDrawingSaveResponse response = phoneDrawingService.savePhoneDrawing(userUuid, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(SAVE_SUCCESS_MESSAGE, response));
    }
}
