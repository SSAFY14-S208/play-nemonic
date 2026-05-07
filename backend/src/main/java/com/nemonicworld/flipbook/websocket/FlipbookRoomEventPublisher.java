package com.nemonicworld.flipbook.websocket;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomKickResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventStateResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventType;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomParticipantKickedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomSimpleMessageResponse;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

/**
 * 플립북 방 WebSocket topic/user queue로 이벤트를 발행합니다.
 */
@Component
@RequiredArgsConstructor
public class FlipbookRoomEventPublisher {

    private static final String ROOM_TOPIC_PREFIX = "/topic/flipbook/rooms/";
    private static final String ROOM_USER_QUEUE_PREFIX = "/queue/flipbook/rooms/";
    private static final String DUPLICATE_SESSION_CLOSED_MESSAGE = "다른 곳에서 접속되어 연결이 종료되었습니다.";
    private static final String KICKED_FROM_ROOM_MESSAGE = "방장에 의해 강퇴되었습니다.";
    private static final CloseStatus KICKED_FROM_ROOM_CLOSE_STATUS = CloseStatus.POLICY_VIOLATION
        .withReason("KICKED_FROM_ROOM");
    private static final String PONG_MESSAGE = "pong";

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    /**
     * 참여자 연결 상태가 바뀐 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishParticipantConnected(FlipbookRoomStateResponse roomStateResponse) {
        publishRoomEvent(FlipbookRoomEventType.PARTICIPANT_CONNECTED, roomStateResponse);
    }

    /**
     * 참여자 연결 해제 상태가 반영된 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishParticipantDisconnected(FlipbookRoomStateResponse roomStateResponse) {
        publishRoomEvent(FlipbookRoomEventType.PARTICIPANT_DISCONNECTED, roomStateResponse);
    }

    /**
     * 방 설정 변경이 반영된 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishSettingsChanged(FlipbookRoomStateResponse roomStateResponse) {
        publishRoomEvent(FlipbookRoomEventType.SETTINGS_CHANGED, roomStateResponse);
    }

    /**
     * 대기실 참여자가 강퇴되었음을 방 전체에 알립니다.
     */
    public void publishParticipantKicked(FlipbookRoomKickResponse kickResponse) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.PARTICIPANT_KICKED,
            kickResponse.roomCode(), FlipbookRoomParticipantKickedEventResponse.from(kickResponse));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + kickResponse.roomCode(), event);
    }

    /**
     * 강퇴 대상자의 현재 개인 큐에 안내를 보낸 뒤 같은 서버의 활성 WebSocket 세션을 종료합니다.
     */
    public void publishKickedFromRoom(String roomCode, String kickedUserUuid) {
        webSocketSessionRegistry
            .findCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK, roomCode, kickedUserUuid)
            .ifPresent(session -> publishKickedFromRoom(roomCode, session));
    }

    /**
     * 같은 roomCode + UUID로 교체된 기존 세션 개인 큐에 중복 접속 종료 안내를 보냅니다.
     */
    public void publishDuplicateSessionClosed(String sessionId, String roomCode) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.DUPLICATE_SESSION_CLOSED,
            roomCode, new FlipbookRoomSimpleMessageResponse(DUPLICATE_SESSION_CLOSED_MESSAGE));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    /**
     * ping을 보낸 현재 세션 개인 큐에 pong 이벤트를 보냅니다.
     */
    public void publishPong(String sessionId, String roomCode) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.PONG, roomCode,
            new FlipbookRoomSimpleMessageResponse(PONG_MESSAGE));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    /**
     * WebSocket 처리 중 클라이언트에 알려도 되는 안전한 오류 메시지를 개인 큐로 전달합니다.
     */
    public void publishError(String sessionId, String roomCode, String message) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.ERROR, roomCode,
            new FlipbookRoomSimpleMessageResponse(message));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    private void publishRoomEvent(FlipbookRoomEventType type, FlipbookRoomStateResponse roomStateResponse) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(type, roomStateResponse.roomCode(),
            FlipbookRoomEventStateResponse.from(roomStateResponse));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomStateResponse.roomCode(), event);
    }

    private void publishKickedFromRoom(String roomCode, ActiveWebSocketSession session) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.KICKED_FROM_ROOM, roomCode,
            new FlipbookRoomSimpleMessageResponse(KICKED_FROM_ROOM_MESSAGE));

        messagingTemplate.convertAndSendToUser(session.sessionId(), ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(session.sessionId()));
        webSocketSessionRegistry.removeStaleSession(session.sessionId());
        webSocketSessionRegistry.closeWebSocketSession(session.sessionId(), KICKED_FROM_ROOM_CLOSE_STATUS);
    }

    private MessageHeaders createSessionHeaders(String sessionId) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);

        return headerAccessor.getMessageHeaders();
    }
}
