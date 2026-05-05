package com.nemonicworld.relay.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventStateResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventType;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 릴레이 WebSocket 이벤트 발행 시 방 전체 topic과 개인 session queue 라우팅 헤더를 검증합니다.
 */
class RelayRoomEventPublisherTest {

    private static final String ROOM_CODE = "QUDNKQ";
    private static final String SESSION_ID = "session-1";

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final RelayRoomEventPublisher publisher = new RelayRoomEventPublisher(messagingTemplate);

    /**
     * 설정 변경 이벤트는 방 전체 topic에 SETTINGS_CHANGED 타입과 최신 방 상태를 보냅니다.
     */
    @Test
    void publishSettingsChangedSendsSettingsChangedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        RelayRoomStateResponse roomStateResponse = roomStateResponse(45);

        publisher.publishSettingsChanged(roomStateResponse);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.SETTINGS_CHANGED);
        assertThat(event.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(event.data()).isInstanceOf(RelayRoomEventStateResponse.class);

        RelayRoomEventStateResponse data = (RelayRoomEventStateResponse) event.data();
        assertThat(data.timeLimitSeconds()).isEqualTo(45);
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
    }

    /**
     * 게임 시작 이벤트는 방 전체 topic에 GAME_STARTED 타입과 현재 파트 타이밍을 함께 보냅니다.
     */
    @Test
    void publishGameStartedSendsGameStartedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        RelayRoomStateResponse roomStateResponse = startedRoomStateResponse();

        publisher.publishGameStarted(roomStateResponse);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.GAME_STARTED);

        RelayRoomEventStateResponse data = (RelayRoomEventStateResponse) event.data();
        assertThat(data.status()).isEqualTo(RelayRoomStatus.PLAYING);
        assertThat(data.currentPart()).isEqualTo(RelayDrawingPart.FACE);
        assertThat(data.assignmentCount()).isEqualTo(6);
        assertThat(data.partDeadlineAt()).isEqualTo(roomStateResponse.partDeadlineAt());
    }

    /**
     * 파트 시작 이벤트는 동일한 방 상태 payload를 PART_STARTED 타입으로 보냅니다.
     */
    @Test
    void publishPartStartedSendsPartStartedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        RelayRoomStateResponse roomStateResponse = startedRoomStateResponse();

        publisher.publishPartStarted(roomStateResponse);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.PART_STARTED);

        RelayRoomEventStateResponse data = (RelayRoomEventStateResponse) event.data();
        assertThat(data.currentPart()).isEqualTo(RelayDrawingPart.FACE);
        assertThat(data.partStartedAt()).isEqualTo(roomStateResponse.partStartedAt());
    }

    /**
     * 개인 큐 이벤트는 user destination resolver가 특정 sessionId로 해석할 수 있도록 simpSessionId
     * 헤더를 함께 보냅니다.
     */
    @Test
    void publishPongSendsSessionIdHeaderForUserQueueRouting() {
        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);

        publisher.publishPong(SESSION_ID, ROOM_CODE);

        verify(messagingTemplate).convertAndSendToUser(eq(SESSION_ID), eq("/queue/relay/rooms/" + ROOM_CODE),
            any(RelayRoomEventResponse.class), headersCaptor.capture());
        assertThat(headersCaptor.getValue()).containsEntry(SimpMessageHeaderAccessor.SESSION_ID_HEADER, SESSION_ID);
    }

    private RelayRoomStateResponse roomStateResponse(int timeLimitSeconds) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1);

        return new RelayRoomStateResponse(ROOM_CODE, RelayRoomStatus.WAITING, "550e8400-e29b-41d4-a716-446655440000",
            timeLimitSeconds, 2, 6, 1, null, List.of(), null, createdAt, createdAt.plusSeconds(1));
    }

    private RelayRoomStateResponse startedRoomStateResponse() {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1);
        LocalDateTime startedAt = createdAt.plusSeconds(10);

        return new RelayRoomStateResponse(ROOM_CODE, RelayRoomStatus.PLAYING, "550e8400-e29b-41d4-a716-446655440000",
            45, 2, 6, 2, RelayDrawingPart.FACE, 6, startedAt, startedAt.plusSeconds(45), startedAt, List.of(), null,
            createdAt, startedAt);
    }
}
