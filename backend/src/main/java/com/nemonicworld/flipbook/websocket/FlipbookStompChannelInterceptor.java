package com.nemonicworld.flipbook.websocket;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.service.FlipbookRoomService;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 플립북 STOMP CONNECT frame을 검증하고 활성 세션을 등록합니다.
 */
@Component
@RequiredArgsConstructor
public class FlipbookStompChannelInterceptor implements ChannelInterceptor {

    private static final String ROOM_CODE_CONNECT_HEADER = "roomCode";
    private static final String CONNECTION_REJECTED_MESSAGE = "플립북 웹소켓 연결을 허용할 수 없습니다.";

    private final FlipbookRoomService flipbookRoomService;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final ObjectProvider<FlipbookRoomEventPublisher> flipbookRoomEventPublisherProvider;

    /**
     * CONNECT frame의 roomCode와 Anonymous-User-UUID를 검증하고 중복 세션을 교체합니다.
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (accessor.getCommand() != StompCommand.CONNECT || !isFlipbookConnection(accessor)) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        // STOMP CONNECT
        // 프론트에서 보내는 정보
        // connectHeaders: {
        // roomCode: "8VU792",
        // "Anonymous-User-UUID": userUuid,
        // }
        String roomCode = accessor.getFirstNativeHeader(ROOM_CODE_CONNECT_HEADER);
        String userUuid = accessor.getFirstNativeHeader(AnonymousUserHeaders.ANONYMOUS_USER_UUID);

        try {
            // UUID가 유효한지, roodCode 형식 확인, Redis에 방이 있는지, 이 사용자가 해당 방 participants에 있는지,
            // Redis에 connected=true로 변경
            FlipbookRoomStateResponse roomStateResponse = flipbookRoomService.connectRoom(userUuid, roomCode);

            configureSession(accessor, sessionId, roomCode, userUuid);
            Optional<ActiveWebSocketSession> replacedSession = webSocketSessionRegistry
                .register(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK, roomCode, userUuid, sessionId);
            FlipbookRoomEventPublisher flipbookRoomEventPublisher = flipbookRoomEventPublisherProvider.getObject();

            replacedSession.ifPresent(session -> closeDuplicateSession(flipbookRoomEventPublisher, session.sessionId(),
                session.connectionKey()));

            // /topic/flipbook/rooms/{roomCode}로 PARTICIPANT_CONNECTED 이벤트를 보냄
            flipbookRoomEventPublisher.publishParticipantConnected(roomStateResponse);

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

        // 사용자 큐를 세션 단위로 격리하기 위해 Principal name은 STOMP sessionId로 둡니다.
        Principal sessionPrincipal = () -> sessionId;
        accessor.setUser(sessionPrincipal);
    }

    private boolean isFlipbookConnection(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

        return sessionAttributes != null && WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK
            .equals(sessionAttributes.get(WebSocketSessionAttributes.CONNECTION_TYPE));
    }

    private void closeDuplicateSession(FlipbookRoomEventPublisher flipbookRoomEventPublisher, String sessionId,
        String roomCode) {
        flipbookRoomEventPublisher.publishDuplicateSessionClosed(sessionId, roomCode);
        webSocketSessionRegistry.closeWebSocketSession(sessionId);
        webSocketSessionRegistry.removeStaleSession(sessionId);
    }
}
