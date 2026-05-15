package com.nemonicworld.infinitecanvas.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiCommonResponses;
import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasParticipantUpdateRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLeaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.service.InfiniteCanvasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
    private static final String CREATE_SUCCESS_MESSAGE = "무한 캔버스 생성 성공";
    private static final String GET_SUCCESS_MESSAGE = "무한 캔버스 조회 성공";
    private static final String PARTICIPANT_UPDATED_MESSAGE = "무한 캔버스 참여자 정보 수정 성공";
    private static final String LEAVE_SUCCESS_MESSAGE = "무한 캔버스 퇴장 성공";

    private final InfiniteCanvasService infiniteCanvasService;

    public InfiniteCanvasController(InfiniteCanvasService infiniteCanvasService) {
        this.infiniteCanvasService = infiniteCanvasService;
    }

    @PostMapping
    @Operation(summary = "무한 캔버스 생성", description = "익명 사용자를 첫 참여자로 등록하고 Redis에 활성 무한 캔버스를 생성합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "무한 캔버스 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<InfiniteCanvasStateResponse>> createCanvas(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestBody(required = false) InfiniteCanvasCreateRequest request) {
        InfiniteCanvasStateResponse response = infiniteCanvasService.createCanvas(userUuid, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CREATE_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/{canvasId}")
    @Operation(summary = "활성 무한 캔버스 조회/참여", description = "활성 캔버스 상태를 조회하고 요청자가 아직 참여자가 아니면 참여자로 등록합니다.")
    @Parameter(name = "canvasId", in = ParameterIn.PATH, required = true, description = "캔버스 ID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "무한 캔버스 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<InfiniteCanvasStateResponse>> getCanvas(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("canvasId") String canvasId) {
        InfiniteCanvasStateResponse response = infiniteCanvasService.getCanvas(userUuid, canvasId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(GET_SUCCESS_MESSAGE, response));
    }

    @PatchMapping("/{canvasId}/participants/me")
    @Operation(summary = "내 무한 캔버스 참여자 정보 수정", description = "현재 캔버스 안에서 표시할 닉네임, 색상, 아바타 정보를 수정합니다.")
    @Parameter(name = "canvasId", in = ParameterIn.PATH, required = true, description = "캔버스 ID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "무한 캔버스 참여자 정보 수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<InfiniteCanvasParticipantResponse>> updateMyParticipant(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("canvasId") String canvasId,
        @RequestBody(required = false) InfiniteCanvasParticipantUpdateRequest request) {
        InfiniteCanvasParticipantResponse response = infiniteCanvasService.updateMyParticipant(userUuid, canvasId,
            request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(PARTICIPANT_UPDATED_MESSAGE, response));
    }

    @DeleteMapping("/{canvasId}/participants/me")
    @Operation(summary = "무한 캔버스 나가기", description = "요청자를 활성 캔버스에서 제거하고 마지막 참여자라면 Redis 상태를 즉시 삭제합니다.")
    @Parameter(name = "canvasId", in = ParameterIn.PATH, required = true, description = "캔버스 ID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "무한 캔버스 퇴장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<InfiniteCanvasLeaveResponse>> leaveCanvas(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("canvasId") String canvasId) {
        InfiniteCanvasLeaveResponse response = infiniteCanvasService.leaveCanvas(userUuid, canvasId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LEAVE_SUCCESS_MESSAGE, response));
    }
}
