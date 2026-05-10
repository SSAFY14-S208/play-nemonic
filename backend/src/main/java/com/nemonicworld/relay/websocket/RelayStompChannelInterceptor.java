package com.nemonicworld.relay.websocket;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.service.RelayRoomService;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

/**
 * 릴레이 STOMP CONNECT frame을 검증하고 활성 세션을 등록합니다.
 */
@Component
public class RelayStompChannelInterceptor implements ChannelInterceptor {

    private static final String ROOM_CODE_CONNECT_HEADER = "roomCode";
    private static final String CONNECTION_REJECTED_MESSAGE = "릴레이 웹소켓 연결을 허용할 수 없습니다.";

    private final RelayRoomService relayRoomService;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final ObjectProvider<RelayRoomEventPublisher> relayRoomEventPublisherProvider;

    public RelayStompChannelInterceptor(RelayRoomService relayRoomService,
        WebSocketSessionRegistry webSocketSessionRegistry,
        ObjectProvider<RelayRoomEventPublisher> relayRoomEventPublisherProvider) {
        this.relayRoomService = relayRoomService;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.relayRoomEventPublisherProvider = relayRoomEventPublisherProvider;
    }

    /**
     * CONNECT frame의 roomCode와 Anonymous-User-UUID를 검증하고 중복 세션을 교체합니다.
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (accessor.getCommand() != StompCommand.CONNECT || !isRelayConnection(accessor)) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        String roomCode = accessor.getFirstNativeHeader(ROOM_CODE_CONNECT_HEADER);
        String userUuid = accessor.getFirstNativeHeader(AnonymousUserHeaders.ANONYMOUS_USER_UUID);

        try {
            RelayRoomStateResponse roomStateResponse = relayRoomService.connectRoom(userUuid, roomCode);
            configureSession(accessor, sessionId, roomCode, userUuid);
            Optional<ActiveWebSocketSession> replacedSession = webSocketSessionRegistry
                .register(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY, roomCode, userUuid, sessionId);
            RelayRoomEventPublisher relayRoomEventPublisher = relayRoomEventPublisherProvider.getObject();

            replacedSession.ifPresent(session -> closeDuplicateSession(relayRoomEventPublisher, session.sessionId(),
                session.connectionKey(), session.userUuid(), sessionId));
            relayRoomEventPublisher.publishParticipantConnected(roomStateResponse, userUuid);
            RelayRoomEventLogger.websocketBusiness("relay_ws_connected",
                metadata("room_id", roomCode, "uuid", userUuid, "session_id", sessionId, "participant_count",
                    roomStateResponse.participantCount(), "is_host",
                    userUuid.equals(roomStateResponse.hostUserUuid())));
            RelayRoomEventLogger.websocketBusiness("relay_room_state_snapshot_sent",
                metadata("room_id", roomCode, "uuid", userUuid, "room_status", roomStateResponse.status(),
                    "current_part", roomStateResponse.currentPart(), "participant_count",
                    roomStateResponse.participantCount()));

            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        } catch (RuntimeException e) {
            webSocketSessionRegistry.removeStaleSession(sessionId);
            RelayRoomEventLogger.websocketBusiness("relay_ws_connection_rejected",
                metadata("room_id", roomCode, "uuid", userUuid, "session_id", sessionId, "reject_reason",
                    e.getMessage(), "exception_type", e.getClass().getSimpleName()));
            throw new MessageDeliveryException(message, CONNECTION_REJECTED_MESSAGE, e);
        }
    }

    private void configureSession(StompHeaderAccessor accessor, String sessionId, String roomCode, String userUuid) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

        if (sessionAttributes != null) {
            sessionAttributes.put(WebSocketSessionAttributes.CONNECTION_KEY, roomCode);
            sessionAttributes.put(WebSocketSessionAttributes.USER_UUID, userUuid);
        }

        // 사용자 큐를 세션 단위로 격리하기 위해 Principal name은 STOMP sessionId로 둡니다.
        Principal sessionPrincipal = () -> sessionId;
        accessor.setUser(sessionPrincipal);
    }

    private boolean isRelayConnection(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

        return sessionAttributes != null && WebSocketSessionAttributes.CONNECTION_TYPE_RELAY
            .equals(sessionAttributes.get(WebSocketSessionAttributes.CONNECTION_TYPE));
    }

    private void closeDuplicateSession(RelayRoomEventPublisher relayRoomEventPublisher, String sessionId,
        String roomCode, String userUuid, String newSessionId) {
        RelayRoomEventLogger.websocketBusiness("relay_duplicate_session_closed", metadata("room_id", roomCode, "uuid",
            userUuid, "old_session_id", sessionId, "new_session_id", newSessionId));
        relayRoomEventPublisher.publishDuplicateSessionClosed(sessionId, roomCode);
        webSocketSessionRegistry.closeWebSocketSession(sessionId);
        webSocketSessionRegistry.removeStaleSession(sessionId);
    }
}
