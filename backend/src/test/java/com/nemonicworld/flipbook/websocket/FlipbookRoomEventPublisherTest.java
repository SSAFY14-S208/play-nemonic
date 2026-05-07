package com.nemonicworld.flipbook.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomKickResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomParticipantResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventStateResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventType;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomParticipantKickedEventResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.CloseStatus;

/**
 * 플립북 WebSocket 이벤트 발행 시 방 전체 topic과 개인 session queue 라우팅 헤더를 검증합니다.
 */
class FlipbookRoomEventPublisherTest {

    private static final String ROOM_CODE = "FB3K9Q";
    private static final String SESSION_ID = "session-1";

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final FlipbookRoomEventPublisher publisher = new FlipbookRoomEventPublisher(messagingTemplate,
        webSocketSessionRegistry);

    /**
     * 설정 변경 이벤트는 방 전체 topic에 SETTINGS_CHANGED 타입과 최신 방 상태를 보냅니다.
     */
    @Test
    void publishSettingsChangedSendsSettingsChangedEventToRoomTopic() {
        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        FlipbookRoomStateResponse roomStateResponse = roomStateResponse(60);

        publisher.publishSettingsChanged(roomStateResponse);

        verify(messagingTemplate).convertAndSend(eq("/topic/flipbook/rooms/" + ROOM_CODE), eventCaptor.capture());
        FlipbookRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(FlipbookRoomEventType.SETTINGS_CHANGED);
        assertThat(event.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(event.data()).isInstanceOf(FlipbookRoomEventStateResponse.class);

        FlipbookRoomEventStateResponse data = (FlipbookRoomEventStateResponse) event.data();
        assertThat(data.timeLimitSeconds()).isEqualTo(60);
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
    }

    /**
     * 개인 큐 이벤트는 user destination resolver가 특정 sessionId로 해석할 수 있도록 simpSessionId
     * 헤더를 함께 보냅니다.
     */
    @Test
    void publishPongSendsSessionIdHeaderForUserQueueRouting() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);

        publisher.publishPong(SESSION_ID, ROOM_CODE);

        verify(messagingTemplate).convertAndSendToUser(eq(SESSION_ID), eq("/queue/flipbook/rooms/" + ROOM_CODE),
            any(FlipbookRoomEventResponse.class), headersCaptor.capture());
        assertThat(headersCaptor.getValue()).containsEntry(SimpMessageHeaderAccessor.SESSION_ID_HEADER, SESSION_ID);
    }

    /**
     * 강퇴 이벤트는 방 전체 topic에 PARTICIPANT_KICKED 타입과 강퇴 대상 정보를 보냅니다.
     */
    @Test
    void publishParticipantKickedSendsParticipantKickedEventToRoomTopic() {
        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        LocalDateTime kickedAt = LocalDateTime.now().minusSeconds(1);
        FlipbookRoomKickResponse response = new FlipbookRoomKickResponse(ROOM_CODE,
            "11111111-1111-1111-1111-111111111111", "포도", 2, kickedAt);

        publisher.publishParticipantKicked(response);

        verify(messagingTemplate).convertAndSend(eq("/topic/flipbook/rooms/" + ROOM_CODE), eventCaptor.capture());
        FlipbookRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(FlipbookRoomEventType.PARTICIPANT_KICKED);
        assertThat(event.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(event.data()).isInstanceOf(FlipbookRoomParticipantKickedEventResponse.class);

        FlipbookRoomParticipantKickedEventResponse data = (FlipbookRoomParticipantKickedEventResponse) event.data();
        assertThat(data.kickedUserUuid()).isEqualTo(response.kickedUserUuid());
        assertThat(data.kickedNickname()).isEqualTo("포도");
        assertThat(data.participantCount()).isEqualTo(2);
        assertThat(data.kickedAt()).isEqualTo(kickedAt);
    }

    /**
     * 강퇴 대상자의 현재 개인 큐에 안내를 보낸 뒤 활성 WebSocket 세션을 정책 위반 status로 종료합니다.
     */
    @Test
    void publishKickedFromRoomSendsUserQueueMessageAndClosesSession() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        String kickedUserUuid = "11111111-1111-1111-1111-111111111111";
        org.mockito.BDDMockito
            .given(webSocketSessionRegistry.findCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK,
                ROOM_CODE, kickedUserUuid))
            .willReturn(Optional.of(new ActiveWebSocketSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK,
                ROOM_CODE, kickedUserUuid, SESSION_ID)));

        publisher.publishKickedFromRoom(ROOM_CODE, kickedUserUuid);

        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        verify(messagingTemplate).convertAndSendToUser(eq(SESSION_ID), eq("/queue/flipbook/rooms/" + ROOM_CODE),
            eventCaptor.capture(), headersCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(FlipbookRoomEventType.KICKED_FROM_ROOM);
        assertThat(headersCaptor.getValue()).containsEntry(SimpMessageHeaderAccessor.SESSION_ID_HEADER, SESSION_ID);
        org.mockito.Mockito.verify(webSocketSessionRegistry).removeStaleSession(SESSION_ID);
        org.mockito.Mockito.verify(webSocketSessionRegistry).closeWebSocketSession(SESSION_ID,
            CloseStatus.POLICY_VIOLATION.withReason("KICKED_FROM_ROOM"));
    }

    private FlipbookRoomStateResponse roomStateResponse(int timeLimitSeconds) {
        LocalDateTime now = LocalDateTime.now();

        return new FlipbookRoomStateResponse(ROOM_CODE, FlipbookRoomStatus.WAITING,
            "550e8400-e29b-41d4-a716-446655440000", timeLimitSeconds, 2, 6, 1,
            List.of(new FlipbookRoomParticipantResponse("550e8400-e29b-41d4-a716-446655440000", "망고", true, 0, true)),
            null, now, now);
    }
}
