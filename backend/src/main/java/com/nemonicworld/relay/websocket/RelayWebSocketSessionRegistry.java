package com.nemonicworld.relay.websocket;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * 단일 서버 안에서 릴레이 WebSocket 활성 세션을 roomCode + UUID 기준으로 관리합니다.
 */
@Component
public class RelayWebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(RelayWebSocketSessionRegistry.class);
    private static final CloseStatus DUPLICATE_SESSION_CLOSE_STATUS = CloseStatus.POLICY_VIOLATION
        .withReason("DUPLICATE_SESSION_CLOSED");

    private final ConcurrentMap<String, String> activeSessionIds = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RelayWebSocketSession> relaySessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WebSocketSession> webSocketSessions = new ConcurrentHashMap<>();

    /**
     * 실제 WebSocket 연결 객체를 sessionId 기준으로 저장합니다.
     */
    public void registerWebSocketSession(WebSocketSession session) {
        webSocketSessions.put(session.getId(), session);
    }

    /**
     * transport 레벨에서 닫힌 raw WebSocket 연결 객체를 제거합니다.
     */
    public void removeWebSocketSession(String sessionId) {
        webSocketSessions.remove(sessionId);
    }

    /**
     * roomCode + UUID 조합에 대한 최신 STOMP sessionId를 등록하고, 교체된 기존 세션을 반환합니다.
     */
    public Optional<RelayWebSocketSession> register(String roomCode, String userUuid, String sessionId) {
        RelayWebSocketSession relaySession = new RelayWebSocketSession(roomCode, userUuid, sessionId);
        relaySessions.put(sessionId, relaySession);

        String registryKey = createRegistryKey(roomCode, userUuid);
        String replacedSessionId = activeSessionIds.put(registryKey, sessionId);

        if (replacedSessionId == null || replacedSessionId.equals(sessionId)) {
            return Optional.empty();
        }

        return Optional.ofNullable(relaySessions.get(replacedSessionId));
    }

    /**
     * sessionId로 저장된 릴레이 세션 메타데이터를 조회합니다.
     */
    public Optional<RelayWebSocketSession> findBySessionId(String sessionId) {
        return Optional.ofNullable(relaySessions.get(sessionId));
    }

    /**
     * disconnect 이벤트를 받은 세션이 현재 roomCode + UUID 조합의 최신 세션인지 확인합니다.
     */
    public boolean isCurrentSession(String roomCode, String userUuid, String sessionId) {
        return sessionId.equals(activeSessionIds.get(createRegistryKey(roomCode, userUuid)));
    }

    /**
     * 최신 세션일 때만 활성 세션 매핑을 제거합니다.
     */
    public Optional<RelayWebSocketSession> removeIfCurrent(String sessionId) {
        RelayWebSocketSession relaySession = relaySessions.remove(sessionId);

        if (relaySession == null) {
            return Optional.empty();
        }

        activeSessionIds.remove(createRegistryKey(relaySession.roomCode(), relaySession.userUuid()), sessionId);

        return Optional.of(relaySession);
    }

    /**
     * 중복 접속으로 교체된 이전 세션 메타데이터만 제거합니다.
     */
    public void removeStaleSession(String sessionId) {
        RelayWebSocketSession relaySession = relaySessions.remove(sessionId);

        if (relaySession != null) {
            activeSessionIds.remove(createRegistryKey(relaySession.roomCode(), relaySession.userUuid()), sessionId);
        }
    }

    /**
     * sessionId에 해당하는 실제 WebSocket 연결이 열려 있으면 안전한 close status로 종료합니다.
     */
    public void closeWebSocketSession(String sessionId) {
        WebSocketSession webSocketSession = webSocketSessions.get(sessionId);

        if (webSocketSession == null || !webSocketSession.isOpen()) {
            return;
        }

        try {
            webSocketSession.close(DUPLICATE_SESSION_CLOSE_STATUS);
        } catch (IOException e) {
            log.warn("Failed to close duplicated relay websocket session. sessionId={}", sessionId, e);
        }
    }

    private String createRegistryKey(String roomCode, String userUuid) {
        return roomCode + ":" + userUuid;
    }

    /**
     * STOMP sessionId와 Redis participant 식별자를 함께 보관하는 세션 메타데이터입니다.
     */
    public record RelayWebSocketSession(String roomCode, String userUuid, String sessionId) {
    }
}
