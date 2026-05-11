package com.nemonicworld.invite.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.invite.dto.response.InviteJoinResponse;
import com.nemonicworld.invite.service.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/invites")
@RequiredArgsConstructor
@Tag(name = "Invite", description = "초대 API") // Swagger에서 Invite 그룹으로 묶임
public class InviteController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String SUCCESS_MESSAGE = "방 입장 성공";

    private final InviteService inviteService;

    /**
     * 초대코드로 협동 부스에 입장합니다.
     */
    @PostMapping("/{inviteCode}")
    @Operation(summary = "초대코드로 협동 부스 입장", description = "초대코드를 검증하고 연결된 협동 부스에 참여자로 등록합니다.")
    @Parameter(name = "inviteCode", in = ParameterIn.PATH, required = true, description = "6자리 초대코드")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "방 입장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "초대코드 형식 오류", value = OpenApiErrorExamples.INVALID_INVITE_CODE),
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "닉네임 미설정", value = OpenApiErrorExamples.RELAY_NICKNAME_REQUIRED)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "초대코드 없음", value = OpenApiErrorExamples.INVITE_NOT_FOUND),
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "입장 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "강퇴된 방", value = OpenApiErrorExamples.FLIPBOOK_KICKED_ROOM_REJOIN))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "만료된 초대코드", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVITE_EXPIRED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "입장 불가 상태", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "종료된 방", value = OpenApiErrorExamples.INVITE_ROOM_CLOSED),
            @ExampleObject(name = "게임 진행 중", value = OpenApiErrorExamples.INVITE_GAME_IN_PROGRESS),
            @ExampleObject(name = "정원 초과", value = OpenApiErrorExamples.INVITE_ROOM_FULL)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<InviteJoinResponse>> joinByInviteCode(@PathVariable String inviteCode,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        InviteJoinResponse response = inviteService.joinByInviteCode(inviteCode, userUuid);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(SUCCESS_MESSAGE, response));
    }
}
