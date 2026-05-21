package com.nemonicworld.infinitecanvas.websocket;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.service.InfiniteCanvasService;
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

@Component
public class InfiniteCanvasStompChannelInterceptor implements ChannelInterceptor {

    private static final String ROOM_CODE_CONNECT_HEADER = "roomCode";
    private static final String CONNECTION_REJECTED_MESSAGE = "무한 캔버스 웹소켓 연결을 허용할 수 없습니다.";

    private final InfiniteCanvasService infiniteCanvasService;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final ObjectProvider<InfiniteCanvasEventPublisher> infiniteCanvasEventPublisherProvider;

    public InfiniteCanvasStompChannelInterceptor(InfiniteCanvasService infiniteCanvasService,
        WebSocketSessionRegistry webSocketSessionRegistry,
        ObjectProvider<InfiniteCanvasEventPublisher> infiniteCanvasEventPublisherProvider) {
        this.infiniteCanvasService = infiniteCanvasService;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.infiniteCanvasEventPublisherProvider = infiniteCanvasEventPublisherProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (accessor.getCommand() != StompCommand.CONNECT || !isInfiniteCanvasConnection(accessor)) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        String roomCode = accessor.getFirstNativeHeader(ROOM_CODE_CONNECT_HEADER);
        String userUuid = accessor.getFirstNativeHeader(AnonymousUserHeaders.ANONYMOUS_USER_UUID);

        try {
            InfiniteCanvasStateResponse response = infiniteCanvasService.connectCanvas(userUuid, roomCode);
            configureSession(accessor, sessionId, response.roomCode(), userUuid);
            Optional<ActiveWebSocketSession> replacedSession = webSocketSessionRegistry.register(
                WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS, response.roomCode(), userUuid, sessionId);
            InfiniteCanvasEventPublisher publisher = infiniteCanvasEventPublisherProvider.getObject();

            replacedSession.ifPresent(session -> closeDuplicateSession(publisher, session));
            publisher.publishParticipantConnected(response);

            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        } catch (RuntimeException e) {
            webSocketSessionRegistry.removeStaleSession(sessionId);
            throw new MessageDeliveryException(message, CONNECTION_REJECTED_MESSAGE, e);
        }
    }

    private void configureSession(StompHeaderAccessor accessor, String sessionId, String roomCode, String userUuid) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

        if (sessionAttributes != null) {
            sessionAttributes.put(WebSocketSessionAttributes.CONNECTION_KEY, roomCode);
            sessionAttributes.put(WebSocketSessionAttributes.USER_UUID, userUuid);
        }

        Principal sessionPrincipal = () -> sessionId;
        accessor.setUser(sessionPrincipal);
    }

    private boolean isInfiniteCanvasConnection(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

        return sessionAttributes != null && WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS
            .equals(sessionAttributes.get(WebSocketSessionAttributes.CONNECTION_TYPE));
    }

    private void closeDuplicateSession(InfiniteCanvasEventPublisher publisher, ActiveWebSocketSession session) {
        publisher.publishDuplicateSessionClosed(session.sessionId(), session.connectionKey());
        publisher.closeStaleSession(session);
    }
}
