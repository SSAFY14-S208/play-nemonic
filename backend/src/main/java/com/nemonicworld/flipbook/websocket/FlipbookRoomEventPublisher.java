package com.nemonicworld.flipbook.websocket;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventStateResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventType;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomSimpleMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 플립북 방 WebSocket topic/user queue로 이벤트를 발행합니다.
 */
@Component
@RequiredArgsConstructor
public class FlipbookRoomEventPublisher {

    private static final String ROOM_TOPIC_PREFIX = "/topic/flipbook/rooms/";
    private static final String ROOM_USER_QUEUE_PREFIX = "/queue/flipbook/rooms/";
    private static final String DUPLICATE_SESSION_CLOSED_MESSAGE = "다른 곳에서 접속되어 연결이 종료되었습니다.";
    private static final String PONG_MESSAGE = "pong";

    private final SimpMessagingTemplate messagingTemplate;

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

    private MessageHeaders createSessionHeaders(String sessionId) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);

        return headerAccessor.getMessageHeaders();
    }
}
