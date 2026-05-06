package com.nemonicworld.global.websocket.config;

import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * STOMP CONNECT 처리 단계에서 어떤 콘텐츠의 WebSocket인지 구분할 수 있도록 세션 속성을 저장합니다.
 */
public class WebSocketConnectionTypeHandshakeInterceptor implements HandshakeInterceptor {

    private final String connectionType;

    public WebSocketConnectionTypeHandshakeInterceptor(String connectionType) {
        this.connectionType = connectionType;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
        Map<String, Object> attributes) {
        attributes.put(WebSocketSessionAttributes.CONNECTION_TYPE, connectionType);

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
        Exception exception) {
        // Handshake 이후 별도 정리 작업은 없습니다.
    }
}
