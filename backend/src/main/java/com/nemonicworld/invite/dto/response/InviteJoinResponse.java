package com.nemonicworld.invite.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초대코드 입장 응답")
/**
 * 초대코드 입장 후 프론트 라우팅과 대기실 표시를 위해 필요한 응답입니다.
 */
public record InviteJoinResponse(@Schema(description = "부스 타입", example = "relay") String boothType,
    @Schema(description = "방 ID", example = "AB3K9Q") String roomId,
    @Schema(description = "방 이름", example = "다현의 릴레이 드로잉") String roomName,
    @Schema(description = "방장 닉네임", example = "다현") String hostNickname,
    @Schema(description = "현재 참여 인원", example = "4") int currentParticipants,
    @Schema(description = "최대 참여 인원", example = "6") int maxParticipants,
    @Schema(description = "내 역할", example = "participant") String yourRole,
    @Schema(description = "이미 입장한 사용자 여부", example = "false") boolean alreadyJoined) {
}
