package com.nemonicworld.relay.config;

import com.nemonicworld.relay.websocket.RelayWebSocketSessionRegistry;
import com.nemonicworld.relay.websocket.RelayStompChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * 릴레이 드로잉 대기실과 게임 화면에서 사용할 STOMP WebSocket 설정입니다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class RelayWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final RelayWebSocketSessionRegistry relayWebSocketSessionRegistry;
    private final RelayStompChannelInterceptor relayStompChannelInterceptor;

    public RelayWebSocketConfig(RelayWebSocketSessionRegistry relayWebSocketSessionRegistry,
        RelayStompChannelInterceptor relayStompChannelInterceptor) {
        this.relayWebSocketSessionRegistry = relayWebSocketSessionRegistry;
        this.relayStompChannelInterceptor = relayStompChannelInterceptor;
    }

    /**
     * 클라이언트가 STOMP WebSocket으로 접속할 엔드포인트를 엽니다.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/relay").setAllowedOriginPatterns("*");
    }

    /**
     * 방 전체 topic, 개인 queue, 클라이언트 send prefix를 릴레이 계약에 맞게 설정합니다.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * STOMP CONNECT frame header를 검증하고 릴레이 세션 메타데이터를 등록합니다.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(relayStompChannelInterceptor);
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
                relayWebSocketSessionRegistry.registerWebSocketSession(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session,
                org.springframework.web.socket.CloseStatus closeStatus) throws Exception {
                relayWebSocketSessionRegistry.removeWebSocketSession(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }
}
