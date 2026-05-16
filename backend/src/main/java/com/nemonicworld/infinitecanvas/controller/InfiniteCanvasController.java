package com.nemonicworld.infinitecanvas.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiCommonResponses;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOutputSaveRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCreateResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLeaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOutputSaveResponse;
import com.nemonicworld.infinitecanvas.service.InfiniteCanvasService;
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
    private static final String LEAVE_SUCCESS_MESSAGE = "무한 캔버스 퇴장 성공";
    private static final String OUTPUT_SAVE_SUCCESS_MESSAGE = "무한 캔버스 출력 이미지 저장 성공";

    private final InfiniteCanvasService infiniteCanvasService;

    public InfiniteCanvasController(InfiniteCanvasService infiniteCanvasService) {
        this.infiniteCanvasService = infiniteCanvasService;
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

    @PostMapping("/{canvasId}/outputs")
    @Operation(summary = "무한 캔버스 출력 이미지 저장", description = "업로드 완료된 INFINITE_CANVAS 파일을 무한 캔버스 산출물과 갤러리 항목으로 저장합니다.")
    @Parameter(name = "canvasId", in = ParameterIn.PATH, required = true, description = "캔버스 ID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "무한 캔버스 출력 이미지 저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<InfiniteCanvasOutputSaveResponse>> saveOutput(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("canvasId") String canvasId, @RequestBody InfiniteCanvasOutputSaveRequest request) {
        InfiniteCanvasOutputSaveResponse response = infiniteCanvasService.saveOutput(userUuid, canvasId, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(OUTPUT_SAVE_SUCCESS_MESSAGE, response));
    }
}
