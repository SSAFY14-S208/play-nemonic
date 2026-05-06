package com.nemonicworld.flipbook.service;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerBlockedReason;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 플립북 방 viewer 응답 생성 팩토리입니다.
 */
@Component
@RequiredArgsConstructor
public class FlipbookRoomViewerFactory {

    private final FlipbookRoomPolicy flipbookRoomPolicy;

    /**
     * 요청 사용자가 현재 방에서 어떤 상태인지 계산합니다.
     */
    public FlipbookRoomViewerResponse create(String viewerUserUuid, FlipbookRoomState roomState) {
        Optional<FlipbookRoomParticipant> participant = flipbookRoomPolicy.findParticipant(roomState, viewerUserUuid);

        if (participant.isPresent()) {
            return createParticipantViewerResponse(viewerUserUuid, roomState, participant.get());
        }

        return createNonParticipantViewerResponse(viewerUserUuid, roomState);
    }

    private FlipbookRoomViewerResponse createParticipantViewerResponse(String viewerUserUuid,
        FlipbookRoomState roomState, FlipbookRoomParticipant participant) {
        boolean host = participant.host() || roomState.hostUserUuid().equals(viewerUserUuid);
        boolean canStart = flipbookRoomPolicy.canStart(roomState, host);

        return new FlipbookRoomViewerResponse(viewerUserUuid, true, host, false, canStart, null);
    }

    private FlipbookRoomViewerResponse createNonParticipantViewerResponse(String viewerUserUuid,
        FlipbookRoomState roomState) {
        FlipbookRoomViewerBlockedReason blockedReason = flipbookRoomPolicy.findJoinBlockedReason(roomState);
        boolean canJoin = blockedReason == null;

        return new FlipbookRoomViewerResponse(viewerUserUuid, false, false, canJoin, false, blockedReason);
    }
}
