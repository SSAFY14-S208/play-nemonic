package com.nemonicworld.infinitecanvas.dto.websocket;

import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 참여자 변경처럼 캔버스 요소가 필요 없는 WebSocket 이벤트에 사용하는 경량 payload입니다.
 */
public record InfiniteCanvasParticipantEventResponse(String roomCode, InfiniteCanvasStatus status, String hostUserUuid,
    List<InfiniteCanvasParticipantResponse> participants, InfiniteCanvasParticipantResponse changedParticipant,
    int maxParticipants, long revision, LocalDateTime updatedAt) {

    public InfiniteCanvasParticipantEventResponse {
        participants = participants == null ? List.of() : List.copyOf(participants);
    }

    public static InfiniteCanvasParticipantEventResponse from(InfiniteCanvasStateResponse response,
        String changedUserUuid) {
        return new InfiniteCanvasParticipantEventResponse(response.roomCode(), response.status(),
            response.hostUserUuid(), response.participants(), findChangedParticipant(response, changedUserUuid),
            response.maxParticipants(), response.revision(), response.updatedAt());
    }

    private static InfiniteCanvasParticipantResponse findChangedParticipant(InfiniteCanvasStateResponse response,
        String changedUserUuid) {
        if (changedUserUuid == null) {
            return null;
        }

        return response.participants().stream().filter(participant -> changedUserUuid.equals(participant.userUuid()))
            .findFirst().orElse(null);
    }
}
