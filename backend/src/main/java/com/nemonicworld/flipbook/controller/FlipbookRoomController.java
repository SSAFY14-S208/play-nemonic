package com.nemonicworld.flipbook.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.flipbook.dto.request.FlipbookFrameSubmitRequest;
import com.nemonicworld.flipbook.dto.request.FlipbookRoomKickRequest;
import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookFrameSubmitResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCloseResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCreateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomKickResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomLeaveResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomMyAssignmentResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomResultsResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.service.FlipbookRoomService;
import com.nemonicworld.flipbook.service.finalization.FlipbookRoomFinalizationTriggerService;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private static final String FLIPBOOK_GAME_STARTED_MESSAGE = "플립북 게임 시작 성공";
    private static final String FLIPBOOK_MY_ASSIGNMENT_FOUND_MESSAGE = "내 플립북 프레임 배정 조회 성공";
    private static final String FLIPBOOK_FRAME_SUBMITTED_MESSAGE = "플립북 프레임 제출 성공";
    private static final String FLIPBOOK_RESULTS_FOUND_MESSAGE = "플립북 결과 조회 성공";
    private static final String FLIPBOOK_ROOM_CLOSED_MESSAGE = "플립북 방 종료 성공";
    private static final String FLIPBOOK_ROOM_ALREADY_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String FLIPBOOK_ROOM_PARTICIPANT_KICKED_MESSAGE = "참여자 강퇴 성공";
    private static final String FLIPBOOK_ROOM_LEFT_MESSAGE = "플립북 방 퇴장 성공";

    private final FlipbookRoomService flipbookRoomService;
    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;
    private final FlipbookRoomFinalizationTriggerService flipbookRoomFinalizationTriggerService;

    public FlipbookRoomController(FlipbookRoomService flipbookRoomService,
        FlipbookRoomEventPublisher flipbookRoomEventPublisher,
        FlipbookRoomFinalizationTriggerService flipbookRoomFinalizationTriggerService) {
        this.flipbookRoomService = flipbookRoomService;
        this.flipbookRoomEventPublisher = flipbookRoomEventPublisher;
        this.flipbookRoomFinalizationTriggerService = flipbookRoomFinalizationTriggerService;
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
        flipbookRoomEventPublisher.publishSettingsChanged(response);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FLIPBOOK_ROOM_SETTINGS_UPDATED_MESSAGE, response));
    }

    /**
     * 방장이 대기 중인 플립북 방을 게임 진행 상태로 전환하고 방 전체에 시작 이벤트를 알립니다.
     */
    @PostMapping("/{roomCode}/start")
    @Operation(summary = "플립북 게임 시작", description = "대기 중인 플립북 방을 PLAYING 상태로 전환하고 첫 라운드 제한 시간 정보를 Redis에 저장합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "플립북 게임 시작 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "게임 시작 권한 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.FLIPBOOK_ROOM_PARTICIPANT_REQUIRED),
            @ExampleObject(name = "방장 아님", value = OpenApiErrorExamples.FLIPBOOK_ROOM_HOST_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.FLIPBOOK_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "게임 시작 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "이미 시작됨", value = OpenApiErrorExamples.FLIPBOOK_GAME_ALREADY_STARTED),
            @ExampleObject(name = "종료된 방", value = OpenApiErrorExamples.FLIPBOOK_ROOM_CLOSED),
            @ExampleObject(name = "인원 부족", value = OpenApiErrorExamples.FLIPBOOK_NOT_ENOUGH_PARTICIPANTS),
            @ExampleObject(name = "연결 끊김", value = OpenApiErrorExamples.FLIPBOOK_PARTICIPANTS_DISCONNECTED),
            @ExampleObject(name = "동시 변경 충돌", value = OpenApiErrorExamples.FLIPBOOK_ROOM_START_UPDATE_CONFLICT)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FlipbookRoomStateResponse>> startRoom(@PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        FlipbookRoomStateResponse response = flipbookRoomService.startRoom(userUuid, roomCode);
        flipbookRoomEventPublisher.publishGameStarted(response);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FLIPBOOK_GAME_STARTED_MESSAGE, response));
    }

    /**
     * 현재 참여자가 이번 라운드에 그릴 플립북 프레임 배정과 이전 프레임 힌트를 조회합니다.
     */
    @GetMapping("/{roomCode}/assignments/me")
    @Operation(summary = "내 플립북 프레임 배정 조회", description = "게임 중인 플립북 방에서 현재 사용자가 이번 라운드에 그릴 프레임과 이전 프레임 힌트를 조회합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 플립북 프레임 배정 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "배정 조회 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.FLIPBOOK_ROOM_PARTICIPANT_REQUIRED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.FLIPBOOK_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "배정 조회 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "게임 시작 전", value = OpenApiErrorExamples.FLIPBOOK_GAME_NOT_STARTED),
            @ExampleObject(name = "종료된 방", value = OpenApiErrorExamples.FLIPBOOK_ROOM_CLOSED),
            @ExampleObject(name = "배정 없음", value = OpenApiErrorExamples.FLIPBOOK_CURRENT_ASSIGNMENT_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FlipbookRoomMyAssignmentResponse>> getMyAssignment(
        @PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        FlipbookRoomMyAssignmentResponse response = flipbookRoomService.getMyAssignment(userUuid, roomCode);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FLIPBOOK_MY_ASSIGNMENT_FOUND_MESSAGE, response));
    }

    /**
     * 현재 참여자가 이번 라운드에 배정받은 플립북 프레임을 제출하고 라운드 진행 상태를 갱신합니다.
     */
    @PostMapping("/{roomCode}/rounds/{round}/frames")
    @Operation(summary = "플립북 프레임 제출", description = "업로드 완료된 fileId를 현재 라운드 배정에 연결하고, 라운드 완료 시 다음 라운드 또는 종료 상태로 전환합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = "round", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "플립북 프레임 제출 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE),
            @ExampleObject(name = "fileId 형식 오류", value = OpenApiErrorExamples.INVALID_FILE_ID),
            @ExampleObject(name = "라운드 오류", value = OpenApiErrorExamples.FLIPBOOK_INVALID_ROUND),
            @ExampleObject(name = "배정 불일치", value = OpenApiErrorExamples.FLIPBOOK_ASSIGNMENT_MISMATCH)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "제출 권한 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.FLIPBOOK_ROOM_PARTICIPANT_REQUIRED),
            @ExampleObject(name = "파일 접근 권한 없음", value = OpenApiErrorExamples.FILE_ACCESS_DENIED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.FLIPBOOK_ROOM_NOT_FOUND),
            @ExampleObject(name = "파일 업로드 없음", value = OpenApiErrorExamples.FILE_UPLOAD_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "제출 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "게임 시작 전", value = OpenApiErrorExamples.FLIPBOOK_GAME_NOT_STARTED),
            @ExampleObject(name = "종료된 방", value = OpenApiErrorExamples.FLIPBOOK_ROOM_CLOSED),
            @ExampleObject(name = "배정 없음", value = OpenApiErrorExamples.FLIPBOOK_CURRENT_ASSIGNMENT_NOT_FOUND),
            @ExampleObject(name = "제출 시간 만료", value = OpenApiErrorExamples.FLIPBOOK_SUBMISSION_EXPIRED),
            @ExampleObject(name = "자동 제출됨", value = OpenApiErrorExamples.FLIPBOOK_AUTO_SUBMITTED),
            @ExampleObject(name = "파일 상태 충돌", value = OpenApiErrorExamples.FLIPBOOK_FRAME_FILE_STATUS_CONFLICT),
            @ExampleObject(name = "파일 목적 충돌", value = OpenApiErrorExamples.FLIPBOOK_FRAME_FILE_PURPOSE_CONFLICT),
            @ExampleObject(name = "동시 제출 충돌", value = OpenApiErrorExamples.FLIPBOOK_FRAME_SUBMIT_UPDATE_CONFLICT)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FlipbookFrameSubmitResponse>> submitFrame(
        @PathVariable("roomCode") String roomCode, @PathVariable("round") int round,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestBody(required = false) FlipbookFrameSubmitRequest request) {
        FlipbookFrameSubmitResponse response = flipbookRoomService.submitFrame(userUuid, roomCode, round, request);
        flipbookRoomEventPublisher.publishFrameSubmitted(response);
        publishRoundAdvanceEvent(response);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FLIPBOOK_FRAME_SUBMITTED_MESSAGE, response));
    }

    private void publishRoundAdvanceEvent(FlipbookFrameSubmitResponse response) {
        if (!response.advanced()) {
            return;
        }

        if (response.allRoundsCompleted()) {
            flipbookRoomEventPublisher.publishAllRoundsCompleted(response.roomCode(), response.roomStatus(),
                response.submittedAt());
            flipbookRoomFinalizationTriggerService.triggerFinalizationAsync(response.roomCode());
            return;
        }

        if (response.nextRound() != null) {
            flipbookRoomEventPublisher.publishRoundStarted(response.roomCode(), response.round(), response.nextRound(),
                response.nextRoundStartedAt(), response.nextRoundDeadlineAt());
        }
    }

    /**
     * 방장이 결과 생성이 완료된 플립북 방을 즉시 닫습니다.
     */
    @PostMapping("/{roomCode}/close")
    @Operation(summary = "플립북 방 수동 종료", description = "방장이 결과 생성이 완료된 플립북 방을 즉시 CLOSED 상태로 전환합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "플립북 방 종료 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "종료 권한 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.FLIPBOOK_ROOM_PARTICIPANT_REQUIRED),
            @ExampleObject(name = "방장 아님", value = OpenApiErrorExamples.FLIPBOOK_ROOM_CLOSE_HOST_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.FLIPBOOK_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "종료 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "결과 생성 전", value = OpenApiErrorExamples.FLIPBOOK_CLOSE_BEFORE_RESULT),
            @ExampleObject(name = "게임 진행 중", value = OpenApiErrorExamples.FLIPBOOK_CLOSE_WHILE_PLAYING),
            @ExampleObject(name = "결과 생성 중", value = OpenApiErrorExamples.FLIPBOOK_CLOSE_WHILE_FINALIZING),
            @ExampleObject(name = "동시 변경 충돌", value = OpenApiErrorExamples.FLIPBOOK_ROOM_UPDATE_CONFLICT)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FlipbookRoomCloseResponse>> closeRoom(@PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        FlipbookRoomCloseResponse response = flipbookRoomService.closeRoom(userUuid, roomCode);
        if (!response.alreadyClosed()) {
            flipbookRoomEventPublisher.publishRoomClosed(response.roomCode(), response.closedAt());
        }
        String message = response.alreadyClosed() ? FLIPBOOK_ROOM_ALREADY_CLOSED_MESSAGE : FLIPBOOK_ROOM_CLOSED_MESSAGE;

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ApiResponse.success(message, response));
    }

    /**
     * 플립북 최종 결과를 조회하고, 아직 DB 결과가 없으면 종료된 Redis 방 상태에서 결과를 생성합니다.
     */
    @GetMapping("/{roomCode}/result")
    @Operation(summary = "플립북 결과 조회", description = "플립북 게임 결과 화면에 필요한 프레임 목록, GIF URL, gallery/artifact 정보를 조회합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "플립북 결과 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "결과 조회 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FLIPBOOK_RESULT_ACCESS_DENIED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "결과 없음", value = OpenApiErrorExamples.FLIPBOOK_RESULT_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FlipbookRoomResultsResponse>> getResults(
        @PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        FlipbookRoomResultsResponse response = flipbookRoomService.getResults(userUuid, roomCode);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FLIPBOOK_RESULTS_FOUND_MESSAGE, response));
    }

    /**
     * 방장이 대기 중 방의 일반 참여자를 강퇴하고 강퇴 이벤트를 알립니다.
     */
    @PostMapping("/{roomCode}/kick")
    @Operation(summary = "플립북 방 참여자 강퇴", description = "방장이 WAITING 상태의 플립북 대기실에서 일반 참여자를 강퇴합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "참여자 강퇴 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "강퇴 권한 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.FLIPBOOK_ROOM_PARTICIPANT_REQUIRED),
            @ExampleObject(name = "방장 아님", value = OpenApiErrorExamples.FLIPBOOK_ROOM_KICK_HOST_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.FLIPBOOK_ROOM_NOT_FOUND),
            @ExampleObject(name = "대상 없음", value = OpenApiErrorExamples.FLIPBOOK_KICK_TARGET_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "강퇴 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "대기실 아님", value = OpenApiErrorExamples.FLIPBOOK_WAITING_ROOM_KICK_ONLY),
            @ExampleObject(name = "자기 자신 강퇴", value = OpenApiErrorExamples.FLIPBOOK_SELF_KICK_NOT_ALLOWED),
            @ExampleObject(name = "방장 강퇴", value = OpenApiErrorExamples.FLIPBOOK_HOST_KICK_NOT_ALLOWED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FlipbookRoomKickResponse>> kickParticipant(
        @PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid, // 요청자
        @RequestBody(required = false) FlipbookRoomKickRequest request) { // 강퇴 대상자
        String targetUserUuid = request == null ? null : request.targetUserUuid();
        FlipbookRoomKickResponse response = flipbookRoomService.kickParticipant(userUuid, roomCode, targetUserUuid);
        flipbookRoomEventPublisher.publishParticipantKicked(response);
        flipbookRoomEventPublisher.publishKickedFromRoom(response.roomCode(), response.kickedUserUuid());

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FLIPBOOK_ROOM_PARTICIPANT_KICKED_MESSAGE, response));
    }

    /**
     * 참여자가 대기 중 방에서 스스로 퇴장하고 필요 시 방장 승계나 방 닫힘 이벤트를 알립니다.
     */
    @DeleteMapping("/{roomCode}/participants/me")
    @Operation(summary = "플립북 방 자발적 퇴장", description = "참여자가 WAITING 상태의 플립북 대기실에서 스스로 퇴장합니다. 방장이 나가면 입장 순서 기준 다음 참여자에게 방장을 승계하고, 마지막 참여자가 나가면 방을 CLOSED로 전환합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "플립북 방 퇴장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "퇴장 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.FLIPBOOK_ROOM_PARTICIPANT_REQUIRED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.FLIPBOOK_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "퇴장 불가 상태", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "대기실 아님", value = OpenApiErrorExamples.FLIPBOOK_WAITING_ROOM_LEAVE_ONLY))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FlipbookRoomLeaveResponse>> leaveRoom(@PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        FlipbookRoomLeaveResponse response = flipbookRoomService.leaveRoom(userUuid, roomCode);
        flipbookRoomEventPublisher.publishParticipantLeft(response);
        if (response.hostChanged()) {
            flipbookRoomEventPublisher.publishHostChanged(response);
        }
        if (response.roomClosed()) {
            flipbookRoomEventPublisher.publishRoomClosed(response.roomCode(), response.leftAt());
        }
        flipbookRoomEventPublisher.closeLeftRoomSession(response.roomCode(), response.leftUserUuid());

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FLIPBOOK_ROOM_LEFT_MESSAGE, response));
    }
}
