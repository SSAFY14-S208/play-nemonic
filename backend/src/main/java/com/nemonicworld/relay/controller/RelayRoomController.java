package com.nemonicworld.relay.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/relay/rooms")
@Tag(name = "Relay", description = "릴레이 API")
/**
 * 릴레이 방 생성 HTTP 요청을 받는 컨트롤러입니다.
 *
 * <p>
 * 실제 방 생성 정책과 Redis 저장은 서비스 계층에 위임합니다.
 */
public class RelayRoomController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String RELAY_ROOM_CREATED_MESSAGE = "릴레이 방 생성 성공";

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
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<RelayRoomCreateResponse>> createRoom(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        RelayRoomCreateResponse response = relayRoomService.createRoom(userUuid);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(RELAY_ROOM_CREATED_MESSAGE, response));
    }
}
