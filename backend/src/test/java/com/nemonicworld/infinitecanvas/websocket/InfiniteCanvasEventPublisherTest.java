package com.nemonicworld.infinitecanvas.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasEventResponse;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasEventStateResponse;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasEventType;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.CloseStatus;

/**
 * 무한 캔버스 WebSocket 이벤트 발행 시 방 전체 topic과 세션 종료 정책을 검증합니다.
 */
class InfiniteCanvasEventPublisherTest {

    private static final String ROOM_CODE = "AC3K9Q";
    private static final String USER_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SESSION_ID = "session-1";

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final InfiniteCanvasEventPublisher publisher = new InfiniteCanvasEventPublisher(messagingTemplate,
        webSocketSessionRegistry);

    /**
     * 참여자 연결 이벤트는 요청자별 me를 제거하고 변경 참여자만 포함한 topic payload를 보냅니다.
     */
    @Test
    void publishParticipantConnectedSendsEventStateWithoutMeToRoomTopic() {
        ArgumentCaptor<InfiniteCanvasEventResponse> eventCaptor = ArgumentCaptor
            .forClass(InfiniteCanvasEventResponse.class);
        InfiniteCanvasStateResponse stateResponse = stateResponse(true);

        publisher.publishParticipantConnected(stateResponse);

        verify(messagingTemplate).convertAndSend(eq("/topic/infinite-canvas/canvases/" + ROOM_CODE),
            eventCaptor.capture());
        InfiniteCanvasEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(InfiniteCanvasEventType.PARTICIPANT_CONNECTED);
        assertThat(event.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(event.data()).isInstanceOf(InfiniteCanvasEventStateResponse.class);

        InfiniteCanvasEventStateResponse data = (InfiniteCanvasEventStateResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.changedParticipant()).isNotNull();
        assertThat(data.changedParticipant().userUuid()).isEqualTo(USER_UUID);
        assertThat(data.participants()).hasSize(1);
        assertThat(data.getClass().getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
            .doesNotContain("me");
    }

    /**
     * 캔버스 종료 이벤트는 방 전체 topic에 종료 이벤트를 보낸 뒤 같은 방 현재 세션을 모두 닫습니다.
     */
    @Test
    void publishCanvasClosedClosesCurrentCanvasSessions() {
        ArgumentCaptor<CloseStatus> closeStatusCaptor = ArgumentCaptor.forClass(CloseStatus.class);
        ActiveWebSocketSession session = new ActiveWebSocketSession(
            WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS, ROOM_CODE, USER_UUID, SESSION_ID);
        given(webSocketSessionRegistry.findCurrentSessions(WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS,
            ROOM_CODE)).willReturn(List.of(session));

        publisher.publishCanvasClosed(ROOM_CODE, null);

        verify(webSocketSessionRegistry).closeWebSocketSession(eq(SESSION_ID), closeStatusCaptor.capture());
        assertThat(closeStatusCaptor.getValue().getReason()).isEqualTo("CANVAS_CLOSED");
    }

    private InfiniteCanvasStateResponse stateResponse(boolean connected) {
        InfiniteCanvasParticipantResponse participant = new InfiniteCanvasParticipantResponse(USER_UUID, "망고",
            "#72DDF7", null, true, connected, LocalDateTime.now(), connected ? LocalDateTime.now() : null);
        LocalDateTime now = LocalDateTime.now();

        return new InfiniteCanvasStateResponse(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, USER_UUID, participant,
            List.of(participant), List.of(), List.of(), Map.of(), null, 6, 0L, now, now);
    }
}
