package com.nemonicworld.backoffice.relay.controller;

import com.nemonicworld.backoffice.relay.dto.response.BackofficeRelayRoomListResponse;
import com.nemonicworld.backoffice.relay.service.BackofficeRelayRoomService;
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
@RequestMapping("/backoffice/relay-rooms")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Backoffice Relay Rooms", description = "백오피스 활성 릴레이 드로잉 방 조회 API")
public class BackofficeRelayRoomController {

    private static final String LIST_SUCCESS_MESSAGE = "활성 릴레이 드로잉 방 목록 조회 성공";

    private final BackofficeRelayRoomService backofficeRelayRoomService;

    public BackofficeRelayRoomController(BackofficeRelayRoomService backofficeRelayRoomService) {
        this.backofficeRelayRoomService = backofficeRelayRoomService;
    }

    @GetMapping
    @Operation(summary = "활성 릴레이 드로잉 방 목록 조회", description = "관리자가 백오피스에서 종료되지 않은(WAITING/PLAYING/FINALIZING/FINISHED) 릴레이 드로잉 방을 조회합니다.")
    @Parameter(name = "status", in = ParameterIn.QUERY, description = "방 상태 필터: WAITING, PLAYING, FINALIZING, FINISHED. CLOSED는 허용되지 않습니다.", example = "PLAYING")
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지 번호 (0-based)", example = "0")
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지 크기. 기본 20, 최대 100으로 클램프", example = "20")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "활성 릴레이 드로잉 방 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 파라미터 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BACKOFFICE_RELAY_ROOM_INVALID_STATUS))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED)))})
    public ResponseEntity<ApiResponse<BackofficeRelayRoomListResponse>> getActiveRelayRooms(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "page", required = false) String page,
        @RequestParam(name = "size", required = false) String size) {
        BackofficeRelayRoomListResponse response = backofficeRelayRoomService.getActiveRelayRooms(adminPrincipal,
            status, page, size);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }
}
