package com.nemonicworld.global.websocket.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * 콘텐츠 식별자 + UUID 기준 활성 WebSocket 세션 레지스트리 정책을 검증합니다.
 */
class WebSocketSessionRegistryTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final String USER_UUID = "550e8400-e29b-41d4-a716-446655440000";

    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry();

    /**
     * 같은 roomCode + UUID로 새 세션이 등록되면 기존 세션 메타데이터를 반환하고 최신 세션만 current가 됩니다.
     */
    @Test
    void registerReplacesExistingSessionForSameRoomAndUser() {
        assertThat(registry.register(ROOM_CODE, USER_UUID, "session-1")).isEmpty();

        ActiveWebSocketSession replacedSession = registry.register(ROOM_CODE, USER_UUID, "session-2").orElseThrow();

        assertThat(replacedSession.sessionId()).isEqualTo("session-1");
        assertThat(registry.isCurrentSession(ROOM_CODE, USER_UUID, "session-1")).isFalse();
        assertThat(registry.isCurrentSession(ROOM_CODE, USER_UUID, "session-2")).isTrue();
    }

    /**
     * 교체된 이전 세션을 제거해도 최신 세션 매핑은 지워지지 않습니다.
     */
    @Test
    void removeStaleSessionDoesNotRemoveCurrentSessionMapping() {
        registry.register(ROOM_CODE, USER_UUID, "session-1");
        registry.register(ROOM_CODE, USER_UUID, "session-2");

        registry.removeStaleSession("session-1");

        assertThat(registry.findBySessionId("session-1")).isEmpty();
        assertThat(registry.isCurrentSession(ROOM_CODE, USER_UUID, "session-2")).isTrue();
    }

    /**
     * 최신 세션이 끊긴 경우에만 roomCode + UUID 활성 매핑을 제거합니다.
     */
    @Test
    void removeIfCurrentRemovesOnlyCurrentSessionMapping() {
        registry.register(ROOM_CODE, USER_UUID, "session-1");
        registry.register(ROOM_CODE, USER_UUID, "session-2");

        assertThat(registry.removeIfCurrent("session-2")).isPresent();

        assertThat(registry.isCurrentSession(ROOM_CODE, USER_UUID, "session-2")).isFalse();
    }

    /**
     * 중복 접속으로 닫아야 하는 raw WebSocket 세션이 열려 있으면 정책 위반 close status로 종료합니다.
     */
    @Test
    void closeWebSocketSessionClosesOpenRawSession() throws Exception {
        WebSocketSession webSocketSession = mock(WebSocketSession.class);
        org.mockito.BDDMockito.given(webSocketSession.getId()).willReturn("session-1");
        org.mockito.BDDMockito.given(webSocketSession.isOpen()).willReturn(true);
        registry.registerWebSocketSession(webSocketSession);

        registry.closeWebSocketSession("session-1");

        verify(webSocketSession).close(CloseStatus.POLICY_VIOLATION.withReason("DUPLICATE_SESSION_CLOSED"));
    }

    /**
     * 이미 닫힌 raw WebSocket 세션은 다시 close하지 않습니다.
     */
    @Test
    void closeWebSocketSessionIgnoresClosedRawSession() throws Exception {
        WebSocketSession webSocketSession = mock(WebSocketSession.class);
        org.mockito.BDDMockito.given(webSocketSession.getId()).willReturn("session-1");
        org.mockito.BDDMockito.given(webSocketSession.isOpen()).willReturn(false);
        registry.registerWebSocketSession(webSocketSession);

        registry.closeWebSocketSession("session-1");

        verify(webSocketSession, never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));
    }
}
