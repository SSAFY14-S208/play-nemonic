package com.nemonicworld.flipbook.service.support;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FlipbookRoomParticipantPolicySupport {

    private static final String ROOM_PARTICIPANT_NOT_FOUND_MESSAGE = "플립북 방에 참여하지 않은 사용자입니다.";
    private static final String KICK_TARGET_NOT_FOUND_MESSAGE = "강퇴할 참여자를 찾을 수 없습니다.";
    private static final String NOT_ENOUGH_PARTICIPANTS_MESSAGE = "최소 2명이 모여야 시작할 수 있습니다.";
    private static final String PARTICIPANTS_DISCONNECTED_MESSAGE = "모든 참여자가 웹소켓에 연결되어야 게임을 시작할 수 있습니다.";
    private static final String KICKED_ROOM_REJOIN_FORBIDDEN_MESSAGE = "강퇴된 방에는 다시 입장할 수 없습니다.";
    private static final String RECONNECT_EXPIRED_MESSAGE = "재접속 가능 시간이 만료되어 게임에 다시 참여할 수 없습니다.";

    public Optional<FlipbookRoomParticipant> findParticipant(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .findFirst();
    }

    public FlipbookRoomParticipant requireParticipant(FlipbookRoomState roomState, String userUuid) {
        return findParticipant(roomState, userUuid)
            .orElseThrow(() -> new ForbiddenException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
    }

    public FlipbookRoomParticipant requireConnectionParticipant(FlipbookRoomState roomState, String userUuid) {
        return findParticipant(roomState, userUuid)
            .orElseThrow(() -> new ConflictException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
    }

    public FlipbookRoomParticipant requireKickTargetParticipant(FlipbookRoomState roomState, String targetUserUuid) {
        return findParticipant(roomState, targetUserUuid)
            .orElseThrow(() -> new NotFoundException(KICK_TARGET_NOT_FOUND_MESSAGE));
    }

    public List<FlipbookRoomParticipant> findStartParticipants(FlipbookRoomState roomState) {
        List<FlipbookRoomParticipant> startParticipants = roomState.participants().stream()
            .sorted(Comparator.comparingInt(FlipbookRoomParticipant::joinOrder)).toList();

        if (startParticipants.stream().anyMatch(participant -> !participant.connected())) {
            throw new ConflictException(PARTICIPANTS_DISCONNECTED_MESSAGE);
        }

        if (startParticipants.size() < roomState.minParticipants()) {
            throw new ConflictException(NOT_ENOUGH_PARTICIPANTS_MESSAGE);
        }

        return startParticipants;
    }

    public void validateNotKicked(FlipbookRoomState roomState, String userUuid) {
        if (isKicked(roomState, userUuid)) {
            throw new ForbiddenException(KICKED_ROOM_REJOIN_FORBIDDEN_MESSAGE);
        }
    }

    public void validateNotDropped(FlipbookRoomState roomState, String userUuid) {
        if (isDropped(roomState, userUuid)) {
            throw new ConflictException(RECONNECT_EXPIRED_MESSAGE);
        }
    }

    public boolean isKicked(FlipbookRoomState roomState, String userUuid) {
        return roomState.kickedUserUuids().contains(userUuid);
    }

    public boolean isDropped(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream()
            .anyMatch(participant -> participant.userUuid().equals(userUuid) && participant.dropped());
    }
}
