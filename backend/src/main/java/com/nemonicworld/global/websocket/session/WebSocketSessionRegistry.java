package com.nemonicworld.global.websocket.session;

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
 * 단일 서버 안에서 WebSocket 활성 세션을 콘텐츠 식별자 + UUID 기준으로 관리합니다.
 */
@Component
public class WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);
    private static final CloseStatus DUPLICATE_SESSION_CLOSE_STATUS = CloseStatus.POLICY_VIOLATION
        .withReason("DUPLICATE_SESSION_CLOSED");

    private final ConcurrentMap<String, String> activeSessionIds = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ActiveWebSocketSession> activeSessions = new ConcurrentHashMap<>();
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
     * 콘텐츠 식별자 + UUID 조합에 대한 최신 STOMP sessionId를 등록하고, 교체된 기존 세션을 반환합니다.
     */
    public Optional<ActiveWebSocketSession> register(String connectionKey, String userUuid, String sessionId) {
        ActiveWebSocketSession activeSession = new ActiveWebSocketSession(connectionKey, userUuid, sessionId);
        activeSessions.put(sessionId, activeSession);

        String registryKey = createRegistryKey(connectionKey, userUuid);
        String replacedSessionId = activeSessionIds.put(registryKey, sessionId);

        if (replacedSessionId == null || replacedSessionId.equals(sessionId)) {
            return Optional.empty();
        }

        return Optional.ofNullable(activeSessions.get(replacedSessionId));
    }

    /**
     * sessionId로 저장된 활성 세션 메타데이터를 조회합니다.
     */
    public Optional<ActiveWebSocketSession> findBySessionId(String sessionId) {
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    /**
     * disconnect 이벤트를 받은 세션이 현재 콘텐츠 식별자 + UUID 조합의 최신 세션인지 확인합니다.
     */
    public boolean isCurrentSession(String connectionKey, String userUuid, String sessionId) {
        return sessionId.equals(activeSessionIds.get(createRegistryKey(connectionKey, userUuid)));
    }

    /**
     * 최신 세션일 때만 활성 세션 매핑을 제거합니다.
     */
    public Optional<ActiveWebSocketSession> removeIfCurrent(String sessionId) {
        ActiveWebSocketSession activeSession = activeSessions.remove(sessionId);

        if (activeSession == null) {
            return Optional.empty();
        }

        activeSessionIds.remove(createRegistryKey(activeSession.connectionKey(), activeSession.userUuid()), sessionId);

        return Optional.of(activeSession);
    }

    /**
     * 중복 접속으로 교체된 이전 세션 메타데이터만 제거합니다.
     */
    public void removeStaleSession(String sessionId) {
        ActiveWebSocketSession activeSession = activeSessions.remove(sessionId);

        if (activeSession != null) {
            activeSessionIds.remove(createRegistryKey(activeSession.connectionKey(), activeSession.userUuid()),
                sessionId);
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
            log.warn("Failed to close duplicated websocket session. sessionId={}", sessionId, e);
        }
    }

    private String createRegistryKey(String connectionKey, String userUuid) {
        return connectionKey + ":" + userUuid;
    }

    /**
     * STOMP sessionId와 콘텐츠 참여자 식별자를 함께 보관하는 세션 메타데이터입니다.
     */
    public record ActiveWebSocketSession(String connectionKey, String userUuid, String sessionId) {
    }
}
