package com.nemonicworld.global.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

class WebSocketMetricsTest {

    private SimpleMeterRegistry registry;
    private WebSocketMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new WebSocketMetrics(registry);
        metrics.init();
    }

    @Test
    void countsUniqueConnectedSessions() {
        SessionConnectedEvent connect = connectedEvent("session-1");

        metrics.onConnect(connect);
        metrics.onConnect(connect);

        assertThat(registry.get("nemonic.ws.active.sessions").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("nemonic.ws.connect").counter().count()).isEqualTo(1.0);
    }

    @Test
    void removesSessionOnceOnDisconnect() {
        metrics.onConnect(connectedEvent("session-1"));

        SessionDisconnectEvent disconnect = disconnectEvent("session-1");
        metrics.onDisconnect(disconnect);
        metrics.onDisconnect(disconnect);

        assertThat(registry.get("nemonic.ws.active.sessions").gauge().value()).isZero();
        assertThat(registry.get("nemonic.ws.disconnect").counter().count()).isEqualTo(1.0);
    }

    private SessionConnectedEvent connectedEvent(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        accessor.setSessionId(sessionId);

        return new SessionConnectedEvent(this, createMessage(accessor));
    }

    private SessionDisconnectEvent disconnectEvent(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);

        return new SessionDisconnectEvent(this, createMessage(accessor), sessionId, CloseStatus.NORMAL);
    }

    private Message<byte[]> createMessage(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
