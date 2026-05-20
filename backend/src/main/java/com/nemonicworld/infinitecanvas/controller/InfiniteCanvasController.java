package com.nemonicworld.infinitecanvas.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiCommonResponses;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasAiStickerCreateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasColorUpdateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOutputSaveRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasAiStickerCreateResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCreateResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLeaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOutputSaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.service.InfiniteCanvasAiStickerService;
import com.nemonicworld.infinitecanvas.service.InfiniteCanvasService;
import com.nemonicworld.infinitecanvas.websocket.InfiniteCanvasEventPublisher;
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
@RequestMapping("/infinite-canvas/canvases")
@Tag(name = OpenApiTags.INFINITE_CANVAS, description = OpenApiTags.INFINITE_CANVAS_DESCRIPTION)
public class InfiniteCanvasController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String CREATE_SUCCESS_MESSAGE = "무한 캔버스 방 생성 성공";
    private static final String STATE_FOUND_SUCCESS_MESSAGE = "무한 캔버스 방 상태 조회 성공";
    private static final String LEAVE_SUCCESS_MESSAGE = "무한 캔버스 퇴장 성공";
    private static final String COLOR_UPDATE_SUCCESS_MESSAGE = "무한 캔버스 참여자 색상 수정 성공";
    private static final String OUTPUT_SAVE_SUCCESS_MESSAGE = "무한 캔버스 출력 이미지 저장 성공";
    private static final String AI_STICKER_CREATE_SUCCESS_MESSAGE = "무한 캔버스 AI 스티커 생성 성공";

    private final InfiniteCanvasService infiniteCanvasService;
    private final InfiniteCanvasAiStickerService infiniteCanvasAiStickerService;
    private final InfiniteCanvasEventPublisher infiniteCanvasEventPublisher;

    public InfiniteCanvasController(InfiniteCanvasService infiniteCanvasService,
        InfiniteCanvasAiStickerService infiniteCanvasAiStickerService,
        InfiniteCanvasEventPublisher infiniteCanvasEventPublisher) {
        this.infiniteCanvasService = infiniteCanvasService;
        this.infiniteCanvasAiStickerService = infiniteCanvasAiStickerService;
        this.infiniteCanvasEventPublisher = infiniteCanvasEventPublisher;
    }

    @PostMapping
    @Operation(summary = "무한 캔버스 생성", description = "닉네임을 설정한 익명 사용자를 첫 참여자로 등록하고 Redis에 활성 무한 캔버스를 생성합니다. 요청 body에는 선택적으로 색상만 전달합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "무한 캔버스 방 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "닉네임 미설정", value = OpenApiErrorExamples.INFINITE_CANVAS_NICKNAME_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<InfiniteCanvasCreateResponse>> createCanvas(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestBody(required = false) InfiniteCanvasCreateRequest request) {
        InfiniteCanvasCreateResponse response = infiniteCanvasService.createCanvas(userUuid, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CREATE_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/{roomCode}")
    @Operation(summary = "무한 캔버스 방 상태 조회", description = "공유 링크 진입, 새로고침, WebSocket 연결 전 초기 화면 구성에 필요한 현재 무한 캔버스 방 상태를 조회합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true, description = "공유 방코드")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "무한 캔버스 방 상태 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<InfiniteCanvasStateResponse>> getCanvasState(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("roomCode") String roomCode) {
        InfiniteCanvasStateResponse response = infiniteCanvasService.getCanvasState(userUuid, roomCode);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(STATE_FOUND_SUCCESS_MESSAGE, response));
    }

    @DeleteMapping("/{roomCode}/participants/me")
    @Operation(summary = "무한 캔버스 나가기", description = "요청자를 활성 캔버스에서 제거하고 마지막 참여자라면 Redis 상태를 즉시 삭제합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true, description = "공유 방코드")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "무한 캔버스 퇴장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<InfiniteCanvasLeaveResponse>> leaveCanvas(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("roomCode") String roomCode) {
        InfiniteCanvasLeaveResponse response = infiniteCanvasService.leaveCanvas(userUuid, roomCode);
        infiniteCanvasEventPublisher.publishParticipantLeft(response);
        if (response.hostChanged()) {
            infiniteCanvasEventPublisher.publishHostChanged(response);
        }
        if (response.closed()) {
            infiniteCanvasEventPublisher.publishCanvasClosed(response.roomCode(), response.closedAt());
        }
        infiniteCanvasEventPublisher.closeLeftCanvasSession(response.roomCode(), response.userUuid());

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LEAVE_SUCCESS_MESSAGE, response));
    }

    @PatchMapping("/{roomCode}/participants/me/color")
    @Operation(summary = "내 무한 캔버스 색상 수정", description = "현재 참여자의 색상만 수정합니다. 닉네임과 방장 여부는 변경하지 않습니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true, description = "공유 방코드")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "무한 캔버스 참여자 색상 수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<InfiniteCanvasParticipantResponse>> updateMyColor(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("roomCode") String roomCode,
        @RequestBody(required = false) InfiniteCanvasColorUpdateRequest request) {
        InfiniteCanvasParticipantResponse response = infiniteCanvasService.updateMyColor(userUuid, roomCode, request);
        infiniteCanvasEventPublisher.publishParticipantUpdated(roomCode, response);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(COLOR_UPDATE_SUCCESS_MESSAGE, response));
    }

    @PostMapping("/{roomCode}/outputs")
    @Operation(summary = "무한 캔버스 출력 이미지 저장", description = "업로드 완료된 INFINITE_CANVAS 파일을 무한 캔버스 산출물과 갤러리 항목으로 저장합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true, description = "공유 방코드")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "무한 캔버스 출력 이미지 저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<InfiniteCanvasOutputSaveResponse>> saveOutput(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("roomCode") String roomCode, @RequestBody InfiniteCanvasOutputSaveRequest request) {
        InfiniteCanvasOutputSaveResponse response = infiniteCanvasService.saveOutput(userUuid, roomCode, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(OUTPUT_SAVE_SUCCESS_MESSAGE, response));
    }

    @PostMapping("/{roomCode}/ai-stickers")
    @Operation(summary = "무한 캔버스 AI 스티커 생성", description = "현재 무한 캔버스 참여자가 입력한 프롬프트로 AI 스티커 PNG를 생성하고, 캔버스 image element 초안을 반환합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true, description = "공유 방코드")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "무한 캔버스 AI 스티커 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "AI 스티커 생성 서비스 사용 불가", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INFINITE_CANVAS_AI_STICKER_UNAVAILABLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<InfiniteCanvasAiStickerCreateResponse>> createAiSticker(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("roomCode") String roomCode,
        @RequestBody(required = false) InfiniteCanvasAiStickerCreateRequest request) {
        InfiniteCanvasAiStickerCreateResponse response = infiniteCanvasAiStickerService.createSticker(userUuid,
            roomCode, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(AI_STICKER_CREATE_SUCCESS_MESSAGE, response));
    }
}
