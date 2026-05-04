package com.nemonicworld.global.websocket.session;

/**
 * STOMP session attributes에 저장하는 공통 WebSocket 메타데이터 key입니다.
 */
public final class WebSocketSessionAttributes {

    public static final String CONNECTION_KEY = "webSocketConnectionKey";
    public static final String USER_UUID = "webSocketUserUuid";

    private WebSocketSessionAttributes() {
    }
}
