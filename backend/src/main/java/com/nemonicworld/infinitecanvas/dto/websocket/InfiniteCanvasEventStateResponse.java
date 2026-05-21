package com.nemonicworld.infinitecanvas.dto.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperation;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 방 전체 topic에 노출할 무한 캔버스 상태 스냅샷입니다.
 */
public record InfiniteCanvasEventStateResponse(String roomCode, InfiniteCanvasStatus status, String hostUserUuid,
    List<InfiniteCanvasParticipantResponse> participants, InfiniteCanvasParticipantResponse changedParticipant,
    List<JsonNode> elements, List<InfiniteCanvasOperation> operations, Map<String, InfiniteCanvasLock> locks,
    JsonNode viewport, int maxParticipants, long revision, LocalDateTime createdAt, LocalDateTime updatedAt) {

    /**
     * REST 상태 응답에서 요청자별 me 정보만 제외해 방 전체 이벤트 payload로 변환합니다.
     */
    public static InfiniteCanvasEventStateResponse from(InfiniteCanvasStateResponse response) {
        return from(response, null);
    }

    /**
     * 연결/해제처럼 특정 참여자에 의해 발생한 이벤트에서는 해당 사용자 정보를 함께 담습니다.
     */
    public static InfiniteCanvasEventStateResponse from(InfiniteCanvasStateResponse response, String changedUserUuid) {
        return new InfiniteCanvasEventStateResponse(response.roomCode(), response.status(), response.hostUserUuid(),
            response.participants(), findChangedParticipant(response, changedUserUuid), response.elements(),
            response.operations(), response.locks(), response.viewport(), response.maxParticipants(),
            response.revision(), response.createdAt(), response.updatedAt());
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
