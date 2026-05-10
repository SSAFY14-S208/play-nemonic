package com.nemonicworld.relay.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.relay.dto.request.RelayRoomKickRequest;
import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.relay.dto.request.RelayRoomSubmissionRequest;
import com.nemonicworld.relay.dto.response.RelayRoomCloseResponse;
import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomKickResponse;
import com.nemonicworld.relay.dto.response.RelayRoomLeaveResponse;
import com.nemonicworld.relay.dto.response.RelayRoomMyAssignmentResponse;
import com.nemonicworld.relay.dto.response.RelayRoomResultsResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.service.RelayRoomService;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

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
    private static final String RELAY_ROOM_PARTICIPANT_KICKED_MESSAGE = "참여자 강퇴 성공";
    private static final String RELAY_ROOM_LEFT_MESSAGE = "릴레이 방 퇴장 성공";
    private static final String RELAY_ROOM_SETTINGS_UPDATED_MESSAGE = "릴레이 방 설정 변경 성공";
    private static final String RELAY_GAME_STARTED_MESSAGE = "릴레이 게임 시작 성공";
    private static final String RELAY_MY_ASSIGNMENT_FOUND_MESSAGE = "내 릴레이 배정 조회 성공";
    private static final String RELAY_ROOM_CLOSED_MESSAGE = "릴레이 방 종료 성공";
    private static final String RELAY_ROOM_ALREADY_CLOSED_MESSAGE = "이미 종료된 방입니다.";

    private static final String RELAY_RESULTS_FOUND_MESSAGE = "릴레이 결과 조회 성공";

    private final RelayRoomService relayRoomService;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private static final String RELAY_PART_SUBMITTED_MESSAGE = "릴레이 그림 제출 성공";

    public RelayRoomController(RelayRoomService relayRoomService, RelayRoomEventPublisher relayRoomEventPublisher) {
        this.relayRoomService = relayRoomService;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
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
     * 진행 중인 릴레이 방에서 요청 사용자가 현재 그릴 캔버스와 파트, 힌트를 조회합니다.
     */
    @GetMapping("/{roomCode}/results")
    @Operation(summary = "릴레이 결과 조회", description = "최종 합성 이미지 URL과 canvasIndex별 FACE/BODY/LEGS 작성자 정보를 조회합니다. 파트별 임시 이미지 URL은 응답하지 않습니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "릴레이 결과 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "릴레이 결과 조회 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.RELAY_RESULT_ACCESS_DENIED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "결과 없음", value = OpenApiErrorExamples.RELAY_RESULT_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<RelayRoomResultsResponse>> getResults(@PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        RelayRoomResultsResponse response = relayRoomService.getResults(userUuid, roomCode);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RELAY_RESULTS_FOUND_MESSAGE, response));
    }

    @GetMapping("/{roomCode}/assignments/me")
    @Operation(summary = "릴레이 내 현재 배정 조회", description = "진행 중인 릴레이 방에서 요청 사용자가 현재 그릴 캔버스, 파트, 남은 시간, 이전 파트 힌트를 조회합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 릴레이 배정 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "배정 조회 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.RELAY_ROOM_PARTICIPANT_REQUIRED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.RELAY_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "배정 조회 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "게임 시작 전", value = OpenApiErrorExamples.RELAY_GAME_NOT_STARTED),
            @ExampleObject(name = "종료된 방", value = OpenApiErrorExamples.RELAY_ROOM_CLOSED),
            @ExampleObject(name = "현재 배정 없음", value = OpenApiErrorExamples.RELAY_CURRENT_ASSIGNMENT_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<RelayRoomMyAssignmentResponse>> getMyAssignment(
        @PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        RelayRoomMyAssignmentResponse response = relayRoomService.getMyAssignment(userUuid, roomCode);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RELAY_MY_ASSIGNMENT_FOUND_MESSAGE, response));
    }

    @PostMapping(value = "/{roomCode}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "릴레이 현재 파트 제출", description = "진행 중인 릴레이 방에서 현재 사용자에게 배정된 현재 파트 이미지를 제출합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "릴레이 그림 제출 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 제출 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE),
            @ExampleObject(name = "파일 요청 오류", value = OpenApiErrorExamples.INVALID_BYTE_SIZE),
            @ExampleObject(name = "파일 형식 오류", value = OpenApiErrorExamples.UNSUPPORTED_FILE_TYPE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "제출 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.RELAY_ROOM_PARTICIPANT_REQUIRED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.RELAY_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "제출 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "게임 시작 전", value = OpenApiErrorExamples.RELAY_GAME_NOT_STARTED),
            @ExampleObject(name = "종료된 방", value = OpenApiErrorExamples.RELAY_ROOM_CLOSED),
            @ExampleObject(name = "현재 배정 없음", value = OpenApiErrorExamples.RELAY_CURRENT_ASSIGNMENT_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "파일 크기 초과", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_SIZE_EXCEEDED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "파일 저장 또는 서버 오류", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "파일 저장 오류", value = OpenApiErrorExamples.FILE_STORAGE_ERROR),
            @ExampleObject(name = "서버 오류", value = OpenApiErrorExamples.SERVER_ERROR)}))})
    public ResponseEntity<ApiResponse<RelayRoomSubmissionResponse>> submitCurrentPart(
        @PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestParam(value = "canvasIndex", required = false) Integer canvasIndex,
        @RequestParam(value = "part", required = false) String part,
        @RequestPart(value = "drawingImage", required = false) MultipartFile drawingImage,
        @RequestPart(value = "hintImage", required = false) MultipartFile hintImage) {
        RelayRoomSubmissionRequest request = new RelayRoomSubmissionRequest(canvasIndex, part, drawingImage, hintImage);
        RelayRoomSubmissionResponse response;
        try {
            response = relayRoomService.submitCurrentPart(userUuid, roomCode, request);
        } catch (RuntimeException e) {
            RelayRoomEventLogger.apiBusiness("relay_submission_rejected",
                metadata("room_id", roomCode, "uuid", userUuid, "canvas_index", canvasIndex, "part", part,
                    "reject_reason", e.getMessage(), "exception_type", e.getClass().getSimpleName()));
            throw e;
        }
        if (!response.alreadySubmitted()) {
            relayRoomEventPublisher.publishPartSubmitted(response);
            if (response.advanced() && response.allPartsCompleted()) {
                relayRoomEventPublisher.publishAllPartsCompleted(response);
            } else if (response.advanced()) {
                relayRoomEventPublisher.publishPartStarted(response);
            }
        }

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RELAY_PART_SUBMITTED_MESSAGE, response));
    }

    @PostMapping("/{roomCode}/close")
    @Operation(summary = "릴레이 방 수동 종료", description = "방장이 결과 생성이 완료된 릴레이 방을 즉시 CLOSED 상태로 전환합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "릴레이 방 종료 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "방 종료 권한 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.RELAY_ROOM_PARTICIPANT_REQUIRED),
            @ExampleObject(name = "방장 아님", value = OpenApiErrorExamples.RELAY_ROOM_CLOSE_HOST_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.RELAY_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "방 종료 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "결과 생성 전", value = OpenApiErrorExamples.RELAY_CLOSE_BEFORE_RESULT),
            @ExampleObject(name = "게임 진행 중", value = OpenApiErrorExamples.RELAY_CLOSE_WHILE_PLAYING),
            @ExampleObject(name = "결과 생성 중", value = OpenApiErrorExamples.RELAY_CLOSE_WHILE_FINALIZING)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<RelayRoomCloseResponse>> closeRoom(@PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        RelayRoomCloseResponse response = relayRoomService.closeRoom(userUuid, roomCode);
        if (!response.alreadyClosed()) {
            relayRoomEventPublisher.publishRoomClosed(response.roomCode(), response.closedAt());
        }
        String message = response.alreadyClosed() ? RELAY_ROOM_ALREADY_CLOSED_MESSAGE : RELAY_ROOM_CLOSED_MESSAGE;

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ApiResponse.success(message, response));
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
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "강퇴된 방 재입장 불가", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.RELAY_KICKED_ROOM_REJOIN))),
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

    /**
     * 방장이 대기 중 방의 일반 참여자를 강퇴하고 강퇴 이벤트를 알립니다.
     */
    @PostMapping("/{roomCode}/participants/kick")
    @Operation(summary = "릴레이 방 참여자 강퇴", description = "방장이 WAITING 상태의 릴레이 대기실에서 일반 참여자를 강퇴합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "참여자 강퇴 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "강퇴 권한 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.RELAY_ROOM_PARTICIPANT_REQUIRED),
            @ExampleObject(name = "방장 아님", value = OpenApiErrorExamples.RELAY_ROOM_KICK_HOST_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.RELAY_ROOM_NOT_FOUND),
            @ExampleObject(name = "대상 없음", value = OpenApiErrorExamples.RELAY_KICK_TARGET_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "강퇴 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "대기실 아님", value = OpenApiErrorExamples.RELAY_WAITING_ROOM_KICK_ONLY),
            @ExampleObject(name = "자기 자신 강퇴", value = OpenApiErrorExamples.RELAY_SELF_KICK_NOT_ALLOWED),
            @ExampleObject(name = "방장 강퇴", value = OpenApiErrorExamples.RELAY_HOST_KICK_NOT_ALLOWED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<RelayRoomKickResponse>> kickParticipant(@PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestBody(required = false) RelayRoomKickRequest request) {
        String targetUserUuid = request == null ? null : request.targetUserUuid();
        RelayRoomKickResponse response = relayRoomService.kickParticipant(userUuid, roomCode, targetUserUuid);
        relayRoomEventPublisher.publishParticipantKicked(response);
        relayRoomEventPublisher.publishKickedFromRoom(response.roomCode(), response.kickedUserUuid());

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RELAY_ROOM_PARTICIPANT_KICKED_MESSAGE, response));
    }

    /**
     * 참여자가 대기 중 방에서 스스로 퇴장하고 필요 시 방장 승계나 방 닫힘 이벤트를 알립니다.
     */
    @DeleteMapping("/{roomCode}/participants/me")
    @Operation(summary = "릴레이 방 자발적 퇴장", description = "참여자가 WAITING 상태의 릴레이 대기실에서 스스로 퇴장합니다. 방장이 나가면 입장 순서 기준 다음 참여자에게 방장을 승계하고, 마지막 참여자가 나가면 방을 CLOSED로 전환합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "릴레이 방 퇴장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "퇴장 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.RELAY_ROOM_PARTICIPANT_REQUIRED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.RELAY_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "퇴장 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "대기실 아님", value = OpenApiErrorExamples.RELAY_WAITING_ROOM_LEAVE_ONLY),
            @ExampleObject(name = "종료된 방", value = OpenApiErrorExamples.RELAY_ROOM_CLOSED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<RelayRoomLeaveResponse>> leaveRoom(@PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        RelayRoomLeaveResponse response = relayRoomService.leaveRoom(userUuid, roomCode);
        relayRoomEventPublisher.publishParticipantLeft(response);
        if (response.hostChanged()) {
            relayRoomEventPublisher.publishHostChanged(response);
        }
        if (response.roomClosed()) {
            relayRoomEventPublisher.publishRoomClosed(response.roomCode(), response.leftAt());
        }
        relayRoomEventPublisher.closeLeftRoomSession(response.roomCode(), response.leftUserUuid());

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RELAY_ROOM_LEFT_MESSAGE, response));
    }

    /**
     * 방장이 대기 중 방의 파트별 제한 시간을 변경하고 방 전체에 최신 설정을 알립니다.
     */
    @PatchMapping("/{roomCode}/settings")
    @Operation(summary = "릴레이 방 설정 변경", description = "대기 중 릴레이 방의 방장이 파트별 제한 시간을 30초, 45초, 60초 중 하나로 변경합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "릴레이 방 설정 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE),
            @ExampleObject(name = "제한 시간 오류", value = OpenApiErrorExamples.RELAY_INVALID_TIME_LIMIT_SECONDS)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "설정 변경 권한 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.RELAY_ROOM_PARTICIPANT_REQUIRED),
            @ExampleObject(name = "방장 아님", value = OpenApiErrorExamples.RELAY_ROOM_HOST_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.RELAY_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "설정 변경 불가 상태", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.RELAY_WAITING_ROOM_SETTINGS_ONLY))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<RelayRoomStateResponse>> updateRoomSettings(
        @PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestBody(required = false) RelayRoomSettingsRequest request) {
        RelayRoomStateResponse response = relayRoomService.updateRoomSettings(userUuid, roomCode, request);
        relayRoomEventPublisher.publishSettingsChanged(response);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RELAY_ROOM_SETTINGS_UPDATED_MESSAGE, response));
    }

    /**
     * 방장이 대기 중인 릴레이 방을 게임 진행 상태로 전환하고 방 전체에 시작 이벤트를 알립니다.
     */
    @PostMapping("/{roomCode}/start")
    @Operation(summary = "릴레이 게임 시작", description = "대기 중인 릴레이 방을 PLAYING 상태로 전환하고 참여자 입장 순서 기준 파트 배정표를 Redis에 저장합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true)
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "릴레이 게임 시작 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "방코드 형식 오류", value = OpenApiErrorExamples.INVALID_ROOM_CODE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "게임 시작 권한 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "비참여자", value = OpenApiErrorExamples.RELAY_ROOM_PARTICIPANT_REQUIRED),
            @ExampleObject(name = "방장 아님", value = OpenApiErrorExamples.RELAY_ROOM_HOST_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "방 없음", value = OpenApiErrorExamples.RELAY_ROOM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "게임 시작 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "이미 시작됨", value = OpenApiErrorExamples.RELAY_GAME_ALREADY_STARTED),
            @ExampleObject(name = "종료된 방", value = OpenApiErrorExamples.RELAY_ROOM_CLOSED),
            @ExampleObject(name = "인원 부족", value = OpenApiErrorExamples.RELAY_NOT_ENOUGH_PARTICIPANTS),
            @ExampleObject(name = "연결 끊김", value = OpenApiErrorExamples.RELAY_PARTICIPANTS_DISCONNECTED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<RelayRoomStateResponse>> startRoom(@PathVariable("roomCode") String roomCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        RelayRoomStateResponse response;
        try {
            response = relayRoomService.startRoom(userUuid, roomCode);
        } catch (RuntimeException e) {
            RelayRoomEventLogger.apiBusiness("relay_start_rejected", metadata("room_id", roomCode, "host_uuid",
                userUuid, "reject_reason", e.getMessage(), "exception_type", e.getClass().getSimpleName()));
            throw e;
        }
        relayRoomEventPublisher.publishGameStarted(response);
        relayRoomEventPublisher.publishPartStarted(response);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RELAY_GAME_STARTED_MESSAGE, response));
    }
}
