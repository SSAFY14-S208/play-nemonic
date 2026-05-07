package com.nemonicworld.global.websocket.config;

import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * 여러 실시간 콘텐츠에서 함께 사용할 STOMP WebSocket 전송 설정입니다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final List<ChannelInterceptor> channelInterceptors;

    public WebSocketConfig(WebSocketSessionRegistry webSocketSessionRegistry,
        List<ChannelInterceptor> channelInterceptors) {
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.channelInterceptors = channelInterceptors;
    }

    /**
     * 클라이언트가 STOMP WebSocket으로 접속할 엔드포인트를 엽니다.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/relay")
            .addInterceptors(
                new WebSocketConnectionTypeHandshakeInterceptor(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY))
            .setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws/flipbook")
            .addInterceptors(
                new WebSocketConnectionTypeHandshakeInterceptor(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK))
            .setAllowedOriginPatterns("*");
    }

    /**
     * 전체 topic, 개인 queue, 클라이언트 send prefix를 애플리케이션 공통 규칙으로 설정합니다.
     * /topic -> 방 전체 방송
     * /queue -> 개인 메시지
     * /app   -> 프론트가 서버로 보내는 메시지
     * /user  -> 특정 사용자/세션에게 보내는 메시지
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * 도메인별 STOMP interceptor가 CONNECT frame과 메시지 정책을 처리할 수 있게 등록합니다.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(channelInterceptors.toArray(ChannelInterceptor[]::new));
    }

    /**
     * STOMP sessionId로 실제 WebSocket 연결을 종료할 수 있도록 raw WebSocketSession을 추적합니다.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {

            @Override
            public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session)
                throws Exception {
                webSocketSessionRegistry.registerWebSocketSession(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session,
                org.springframework.web.socket.CloseStatus closeStatus) throws Exception {
                webSocketSessionRegistry.removeWebSocketSession(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }
}
