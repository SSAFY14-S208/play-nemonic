package com.nemonicworld.relay.websocket;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.relay.dto.response.RelayRoomParticipantResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.service.RelayRoomService;
import com.nemonicworld.relay.websocket.RelayWebSocketSessionRegistry.RelayWebSocketSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * STOMP disconnect 이벤트가 최신 세션에 대해서만 Redis 연결 해제를 수행하는지 검증합니다.
 */
class RelayWebSocketEventListenerTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final String USER_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SESSION_ID = "session-1";

    private final RelayRoomService relayRoomService = mock(RelayRoomService.class);
    private final RelayWebSocketSessionRegistry relayWebSocketSessionRegistry = mock(
        RelayWebSocketSessionRegistry.class);
    private final RelayRoomEventPublisher relayRoomEventPublisher = mock(RelayRoomEventPublisher.class);
    private final RelayWebSocketEventListener listener = new RelayWebSocketEventListener(relayRoomService,
        relayWebSocketSessionRegistry, relayRoomEventPublisher);

    /**
     * 최신 활성 세션 disconnect는 Redis connected=false 갱신 후 방 전체 이벤트를 발행합니다.
     */
    @Test
    void handleSessionDisconnectUpdatesRedisWhenSessionIsCurrent() {
        RelayRoomStateResponse roomStateResponse = roomStateResponse();
        RelayWebSocketSession session = new RelayWebSocketSession(ROOM_CODE, USER_UUID, SESSION_ID);
        given(relayWebSocketSessionRegistry.findBySessionId(SESSION_ID)).willReturn(Optional.of(session));
        given(relayWebSocketSessionRegistry.isCurrentSession(ROOM_CODE, USER_UUID, SESSION_ID)).willReturn(true);
        given(relayRoomService.disconnectRoom(USER_UUID, ROOM_CODE)).willReturn(roomStateResponse);

        listener.handleSessionDisconnect(disconnectEvent());

        verify(relayRoomService).disconnectRoom(USER_UUID, ROOM_CODE);
        verify(relayRoomEventPublisher).publishParticipantDisconnected(roomStateResponse);
        verify(relayWebSocketSessionRegistry).removeIfCurrent(SESSION_ID);
    }

    /**
     * 중복 접속으로 교체된 예전 세션의 disconnect는 Redis 상태를 false로 덮어쓰지 않습니다.
     */
    @Test
    void handleSessionDisconnectSkipsRedisUpdateWhenSessionIsStale() {
        RelayWebSocketSession session = new RelayWebSocketSession(ROOM_CODE, USER_UUID, SESSION_ID);
        given(relayWebSocketSessionRegistry.findBySessionId(SESSION_ID)).willReturn(Optional.of(session));
        given(relayWebSocketSessionRegistry.isCurrentSession(ROOM_CODE, USER_UUID, SESSION_ID)).willReturn(false);

        listener.handleSessionDisconnect(disconnectEvent());

        verify(relayRoomService, never()).disconnectRoom(USER_UUID, ROOM_CODE);
        verify(relayRoomEventPublisher, never()).publishParticipantDisconnected(org.mockito.ArgumentMatchers.any());
        verify(relayWebSocketSessionRegistry).removeStaleSession(SESSION_ID);
    }

    private SessionDisconnectEvent disconnectEvent() {
        return new SessionDisconnectEvent(this, MessageBuilder.withPayload(new byte[0]).build(), SESSION_ID,
            CloseStatus.NORMAL);
    }

    private RelayRoomStateResponse roomStateResponse() {
        RelayRoomParticipantResponse participant = new RelayRoomParticipantResponse(USER_UUID, "망고", true, 0, false);
        LocalDateTime now = LocalDateTime.now();

        return new RelayRoomStateResponse(ROOM_CODE, RelayRoomStatus.WAITING, USER_UUID, 60, 2, 6, 1, null,
            List.of(participant), null, now, now);
    }
}
