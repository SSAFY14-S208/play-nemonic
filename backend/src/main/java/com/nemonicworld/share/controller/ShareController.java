package com.nemonicworld.share.controller;

import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.share.dto.request.ShareCreateRequest;
import com.nemonicworld.share.dto.response.ShareCreateResponse;
import com.nemonicworld.share.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
@Tag(name = OpenApiTags.SHARE, description = OpenApiTags.SHARE_DESCRIPTION)
public class ShareController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String SHARE_CREATED_MESSAGE = "공유 정보 생성 성공";

    private final ShareService shareService;

    /**
     * 결과 화면에서 카카오톡/인스타그램 공유에 사용할 공통 재료를 생성합니다.
     */
    @PostMapping
    @Operation(summary = "SNS 공유 정보 생성", description = "산출물 이미지 URL과 카카오톡/인스타그램 공유용 네모닉 사이트 UTM URL을 생성합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "공유 정보 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "갤러리 항목 ID 형식 오류", value = OpenApiErrorExamples.INVALID_GALLERY_ID),
            @ExampleObject(name = "공유 이미지 없음", value = OpenApiErrorExamples.SHARE_IMAGE_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자 또는 갤러리 항목", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "갤러리 항목 없음", value = OpenApiErrorExamples.GALLERY_ITEM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<ShareCreateResponse>> createShare(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @Valid @RequestBody ShareCreateRequest request) {
        ShareCreateResponse response = shareService.createShare(userUuid, request);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(SHARE_CREATED_MESSAGE, response));
    }
}
