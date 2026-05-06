package com.nemonicworld.flipbook.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCreateResponse;
import com.nemonicworld.flipbook.service.FlipbookRoomService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/flipbook/rooms")
@Tag(name = "Flipbook", description = "플립북 API")
public class FlipbookRoomController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String FLIPBOOK_ROOM_CREATED_MESSAGE = "플립북 방 생성 성공";

    private final FlipbookRoomService flipbookRoomService;

    public FlipbookRoomController(FlipbookRoomService flipbookRoomService) {
        this.flipbookRoomService = flipbookRoomService;
    }

    /**
     * 기존 익명 사용자를 방장 겸 첫 참여자로 등록하고 대기 중인 플립북 방을 생성합니다.
     */
    @PostMapping
    @Operation(summary = "플립북 방 생성", description = "기존 익명 사용자를 방장 겸 첫 참여자로 등록하고 Redis에 대기 중인 플립북 방을 생성합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "플립북 방 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "닉네임 미설정", value = OpenApiErrorExamples.FLIPBOOK_NICKNAME_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FlipbookRoomCreateResponse>> createRoom(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        FlipbookRoomCreateResponse response = flipbookRoomService.createRoom(userUuid);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FLIPBOOK_ROOM_CREATED_MESSAGE, response));
    }
}
