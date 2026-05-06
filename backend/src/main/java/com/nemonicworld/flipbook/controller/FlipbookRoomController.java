package com.nemonicworld.flipbook.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCreateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/flipbook/rooms")
@Tag(name = "Flipbook", description = "플립북 API")
public class FlipbookRoomController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String FLIPBOOK_ROOM_CREATED_MESSAGE = "플립북 방 생성 성공";
    private static final String FLIPBOOK_ROOM_STATE_FOUND_MESSAGE = "플립북 방 상태 조회 성공";
    private static final String FLIPBOOK_ROOM_SETTINGS_UPDATED_MESSAGE = "플립북 방 설정 변경 성공";

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

    /**
     * Redis에 저장된 플립북 방 상태를 변경하지 않고 요청자 기준 viewer 상태를 포함해 조회합니다.
     */
    @GetMapping("/{roomCode}")
    @Operation(summary = "플립북 대기실 정보 조회", description = "공유 링크 진입, 새로고침, WebSocket 연결 전 초기 화면 구성에 필요한 현재 플립북 방 상태를 조회합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "플립북 방 상태 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.FLIPBOOK_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FlipbookRoomStateResponse>> getRoomState(
        @PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        FlipbookRoomStateResponse response = flipbookRoomService.getRoomState(userUuid, roomCode);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FLIPBOOK_ROOM_STATE_FOUND_MESSAGE, response));
    }

    /**
     * 방장이 대기 중 방의 라운드별 제한 시간을 변경합니다.
     */
    @PatchMapping("/{roomCode}/settings")
    @Operation(summary = "플립북 방 설정 변경", description = "대기 중 플립북 방의 방장이 라운드별 제한 시간을 30초, 45초, 60초 중 하나로 변경합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "플립북 방 설정 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE),
            @ExampleObject(name = "제한 시간 오류", value = OpenApiErrorExamples.FLIPBOOK_INVALID_TIME_LIMIT_SECONDS)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "설정 변경 권한 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.FLIPBOOK_ROOM_PARTICIPANT_REQUIRED),
            @ExampleObject(name = "방장 아님", value = OpenApiErrorExamples.FLIPBOOK_ROOM_HOST_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.FLIPBOOK_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "설정 변경 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "대기방 상태 아님", value = OpenApiErrorExamples.FLIPBOOK_WAITING_ROOM_SETTINGS_ONLY),
            @ExampleObject(name = "동시 변경 충돌", value = OpenApiErrorExamples.FLIPBOOK_ROOM_UPDATE_CONFLICT)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FlipbookRoomStateResponse>> updateRoomSettings(
        @PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestBody(required = false) FlipbookRoomSettingsRequest request) {
        FlipbookRoomStateResponse response = flipbookRoomService.updateRoomSettings(userUuid, roomCode, request);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FLIPBOOK_ROOM_SETTINGS_UPDATED_MESSAGE, response));
    }
}
