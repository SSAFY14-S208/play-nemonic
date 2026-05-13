package com.nemonicworld.backoffice.flipbook.controller;

import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.auth.service.AdminClientInfoResolver;
import com.nemonicworld.backoffice.flipbook.dto.response.BackofficeFlipbookRoomDeleteResponse;
import com.nemonicworld.backoffice.flipbook.dto.response.BackofficeFlipbookRoomListResponse;
import com.nemonicworld.backoffice.flipbook.service.BackofficeFlipbookRoomService;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiCommonResponses;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice/flipbook-rooms")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = OpenApiTags.BACKOFFICE_FLIPBOOK, description = OpenApiTags.BACKOFFICE_FLIPBOOK_DESCRIPTION)
public class BackofficeFlipbookRoomController {

    private static final String LIST_SUCCESS_MESSAGE = "활성 플립북 방 목록 조회 성공";
    private static final String DELETE_SUCCESS_MESSAGE = "플립북 방 삭제 성공";
    private static final String LIST_OPERATION_DESCRIPTION = "관리자가 백오피스에서 활성 플립북 방"
        + "(WAITING/PLAYING/FINALIZING)을 조회합니다.";
    private static final String STATUS_PARAMETER_DESCRIPTION = "방 상태 필터: WAITING, PLAYING, FINALIZING, "
        + "FINISHED, IN_PROGRESS(PLAYING+FINALIZING). 생략 시 WAITING/PLAYING/FINALIZING. CLOSED는 허용되지 않습니다.";
    private static final String ROOM_DESC = "삭제할 공유 방코드";
    private static final String INVALID_STATUS_EXAMPLE = OpenApiErrorExamples.BACKOFFICE_FLIPBOOK_ROOM_INVALID_STATUS;
    private static final String UPDATE_CONFLICT_EXAMPLE = OpenApiErrorExamples.BACKOFFICE_FLIPBOOK_ROOM_UPDATE_CONFLICT;

    private final BackofficeFlipbookRoomService backofficeFlipbookRoomService;
    private final AdminClientInfoResolver adminClientInfoResolver;

    public BackofficeFlipbookRoomController(BackofficeFlipbookRoomService backofficeFlipbookRoomService,
        AdminClientInfoResolver adminClientInfoResolver) {
        this.backofficeFlipbookRoomService = backofficeFlipbookRoomService;
        this.adminClientInfoResolver = adminClientInfoResolver;
    }

    @GetMapping
    @Operation(summary = "활성 플립북 방 목록 조회", description = LIST_OPERATION_DESCRIPTION)
    @Parameter(name = "status", in = ParameterIn.QUERY, description = STATUS_PARAMETER_DESCRIPTION, example = "PLAYING")
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지 번호 (0-based)", example = "0")
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지 크기. 기본 20, 최대 100으로 클램프", example = "20")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "활성 플립북 방 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 파라미터 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_STATUS_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF)})
    public ResponseEntity<ApiResponse<BackofficeFlipbookRoomListResponse>> getActiveFlipbookRooms(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "page", required = false) String page,
        @RequestParam(name = "size", required = false) String size) {
        BackofficeFlipbookRoomListResponse response = backofficeFlipbookRoomService
            .getActiveFlipbookRooms(adminPrincipal, status, page, size);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }

    @DeleteMapping("/{roomCode}")
    @Operation(summary = "활성 플립북 방 삭제", description = "관리자가 활성 플립북 방을 CLOSED 상태로 강제 전환합니다.")
    @Parameter(name = "roomCode", in = ParameterIn.PATH, required = true, description = ROOM_DESC, example = "AB3K9Q")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "플립북 방 삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "방코드 형식 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_ROOM_CODE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 방", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FLIPBOOK_ROOM_NOT_FOUND))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "방 삭제 충돌", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "이미 종료된 방", value = OpenApiErrorExamples.FLIPBOOK_ROOM_CLOSED),
            @ExampleObject(name = "상태 갱신 충돌", value = UPDATE_CONFLICT_EXAMPLE)}))})
    public ResponseEntity<ApiResponse<BackofficeFlipbookRoomDeleteResponse>> deleteActiveFlipbookRoom(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("roomCode") String roomCode,
        HttpServletRequest servletRequest) {
        BackofficeFlipbookRoomDeleteResponse response = backofficeFlipbookRoomService
            .deleteActiveFlipbookRoom(adminPrincipal, roomCode, adminClientInfoResolver.resolve(servletRequest));

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DELETE_SUCCESS_MESSAGE, response));
    }
}
