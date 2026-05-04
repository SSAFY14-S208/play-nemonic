package com.nemonicworld.relay.websocket;

import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventStateResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventType;
import com.nemonicworld.relay.dto.websocket.RelayRoomSimpleMessageResponse;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Component;

/**
 * 릴레이 방 WebSocket topic/user queue로 이벤트를 발행합니다.
 */
@Component
public class RelayRoomEventPublisher {

    private static final String ROOM_TOPIC_PREFIX = "/topic/relay/rooms/";
    private static final String ROOM_USER_QUEUE_PREFIX = "/queue/relay/rooms/";
    private static final String DUPLICATE_SESSION_CLOSED_MESSAGE = "다른 곳에서 접속되어 연결이 종료되었습니다.";
    private static final String PONG_MESSAGE = "pong";

    private final SimpMessagingTemplate messagingTemplate;

    public RelayRoomEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 참여자 연결 상태가 바뀐 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishParticipantConnected(RelayRoomStateResponse roomStateResponse) {
        publishRoomEvent(RelayRoomEventType.PARTICIPANT_CONNECTED, roomStateResponse);
    }

    /**
     * 참여자 연결 해제 상태가 반영된 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishParticipantDisconnected(RelayRoomStateResponse roomStateResponse) {
        publishRoomEvent(RelayRoomEventType.PARTICIPANT_DISCONNECTED, roomStateResponse);
    }

    /**
     * 명시적인 방 상태 갱신 이벤트가 필요할 때 현재 방 스냅샷을 방 전체에 보냅니다.
     */
    public void publishRoomUpdated(RelayRoomStateResponse roomStateResponse) {
        publishRoomEvent(RelayRoomEventType.ROOM_UPDATED, roomStateResponse);
    }

    /**
     * 같은 roomCode + UUID로 교체된 기존 세션 개인 큐에 중복 접속 종료 안내를 보냅니다.
     */
    public void publishDuplicateSessionClosed(String sessionId, String roomCode) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.DUPLICATE_SESSION_CLOSED, roomCode,
            new RelayRoomSimpleMessageResponse(DUPLICATE_SESSION_CLOSED_MESSAGE));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    /**
     * ping을 보낸 현재 세션 개인 큐에 pong 이벤트를 보냅니다.
     */
    public void publishPong(String sessionId, String roomCode) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.PONG, roomCode,
            new RelayRoomSimpleMessageResponse(PONG_MESSAGE));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    /**
     * WebSocket 처리 중 클라이언트에 알려도 되는 안전한 오류 메시지를 개인 큐로 전달합니다.
     */
    public void publishError(String sessionId, String roomCode, String message) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.ERROR, roomCode,
            new RelayRoomSimpleMessageResponse(message));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    private void publishRoomEvent(RelayRoomEventType type, RelayRoomStateResponse roomStateResponse) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(type, roomStateResponse.roomCode(),
            RelayRoomEventStateResponse.from(roomStateResponse));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomStateResponse.roomCode(), event);
    }

    private MessageHeaders createSessionHeaders(String sessionId) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);

        return headerAccessor.getMessageHeaders();
    }
}
