package com.nemonicworld.backoffice.flipbook.controller;

import com.nemonicworld.backoffice.flipbook.dto.response.BackofficeFlipbookRoomListResponse;
import com.nemonicworld.backoffice.flipbook.service.BackofficeFlipbookRoomService;
import com.nemonicworld.common.jwt.AdminPrincipal;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice/flipbook-rooms")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Backoffice Flipbook Rooms", description = "백오피스 활성 플립북 방 조회 API")
public class BackofficeFlipbookRoomController {

    private static final String LIST_SUCCESS_MESSAGE = "활성 플립북 방 목록 조회 성공";

    private final BackofficeFlipbookRoomService backofficeFlipbookRoomService;

    public BackofficeFlipbookRoomController(BackofficeFlipbookRoomService backofficeFlipbookRoomService) {
        this.backofficeFlipbookRoomService = backofficeFlipbookRoomService;
    }

    @GetMapping
    @Operation(summary = "활성 플립북 방 목록 조회", description = "관리자가 백오피스에서 종료되지 않은(WAITING/PLAYING/FINISHED) 플립북 방을 조회합니다.")
    @Parameter(name = "status", in = ParameterIn.QUERY, description = "방 상태 필터: WAITING, PLAYING, FINISHED. CLOSED는 허용되지 않습니다.", example = "PLAYING")
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지 번호 (0-based)", example = "0")
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지 크기. 기본 20, 최대 100으로 클램프", example = "20")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "활성 플립북 방 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 파라미터 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BACKOFFICE_FLIPBOOK_ROOM_INVALID_STATUS))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED)))})
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
}
