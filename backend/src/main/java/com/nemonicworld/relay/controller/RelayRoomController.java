package com.nemonicworld.relay.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.service.RelayRoomService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/relay/rooms")
@Tag(name = "Relay", description = "릴레이 API")
/**
 * 릴레이 방 생성, 상태 조회, 입장/복귀 HTTP 요청을 받는 컨트롤러입니다.
 *
 * 실제 방 상태 변경과 Redis 조회는 서비스 계층에 위임합니다.
 */
public class RelayRoomController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String RELAY_ROOM_CREATED_MESSAGE = "릴레이 방 생성 성공";
    private static final String RELAY_ROOM_STATE_FOUND_MESSAGE = "릴레이 방 상태 조회 성공";
    private static final String RELAY_ROOM_JOINED_MESSAGE = "릴레이 방 입장/복귀 성공";

    private final RelayRoomService relayRoomService;

    public RelayRoomController(RelayRoomService relayRoomService) {
        this.relayRoomService = relayRoomService;
    }

    /**
     * 기존 익명 사용자를 방장 겸 첫 참여자로 등록하고 대기 중인 릴레이 방을 생성합니다.
     */
    @PostMapping
    @Operation(summary = "릴레이 방 생성", description = "기존 익명 사용자를 방장 겸 첫 참여자로 등록하고 Redis에 대기 중인 릴레이 방을 생성합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "릴레이 방 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "닉네임 미설정", value = OpenApiErrorExamples.RELAY_NICKNAME_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<RelayRoomCreateResponse>> createRoom(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        // 예외 응답은 전역 핸들러가 공통 포맷으로 변환하므로 컨트롤러에서는 정상 흐름만 조립합니다.
        RelayRoomCreateResponse response = relayRoomService.createRoom(userUuid);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RELAY_ROOM_CREATED_MESSAGE, response));
    }

    /**
     * Redis에 저장된 방 상태를 변경하지 않고 요청자 기준 viewer 상태를 포함해 조회합니다.
     */
    @GetMapping("/{roomCode}")
    @Operation(summary = "릴레이 방 상태 조회", description = "공유 링크 진입, 새로고침, WebSocket 연결 전 초기 화면 구성에 필요한 현재 릴레이 방 상태를 조회합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "릴레이 방 상태 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.RELAY_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<RelayRoomStateResponse>> getRoomState(@PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        // 조회 API는 Redis 상태를 바꾸지 않고 서비스가 계산한 현재 스냅샷만 반환합니다.
        RelayRoomStateResponse response = relayRoomService.getRoomState(userUuid, roomCode);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RELAY_ROOM_STATE_FOUND_MESSAGE, response));
    }

    /**
     * 대기 중 방에 신규 참여자를 추가하거나 기존 참여자의 10초 이내 재접속 복귀를 처리합니다.
     */
    @PostMapping("/{roomCode}/participants")
    @Operation(summary = "릴레이 방 입장/복귀", description = "대기 중 릴레이 방에 신규 참여자를 추가하거나 기존 참여자의 재접속 복귀를 Redis 방 상태에 반영합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "릴레이 방 입장/복귀 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE),
            @ExampleObject(name = "닉네임 미설정", value = OpenApiErrorExamples.RELAY_NICKNAME_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.RELAY_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "입장 또는 재접속 불가", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "정원 초과", value = OpenApiErrorExamples.RELAY_ROOM_FULL),
            @ExampleObject(name = "게임 진행 중", value = OpenApiErrorExamples.RELAY_GAME_IN_PROGRESS),
            @ExampleObject(name = "재접속 만료", value = OpenApiErrorExamples.RELAY_RECONNECT_EXPIRED),
            @ExampleObject(name = "종료된 방", value = OpenApiErrorExamples.RELAY_ROOM_CLOSED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<RelayRoomStateResponse>> joinRoom(@PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        RelayRoomStateResponse response = relayRoomService.joinRoom(userUuid, roomCode);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RELAY_ROOM_JOINED_MESSAGE, response));
    }
}
