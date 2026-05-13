package com.nemonicworld.global.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketMetrics {

    private final MeterRegistry registry;
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    private Counter connect;
    private Counter disconnect;

    public WebSocketMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void init() {
        Gauge.builder("nemonic.ws.active.sessions", activeSessions, Set::size)
            .description("Currently connected STOMP sessions").register(registry);

        connect = Counter.builder("nemonic.ws.connect").description("Total STOMP CONNECT events").register(registry);

        disconnect = Counter.builder("nemonic.ws.disconnect").description("Total STOMP DISCONNECT events")
            .register(registry);
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();

        if (sessionId != null && activeSessions.add(sessionId)) {
            connect.increment();
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();

        if (sessionId != null && activeSessions.remove(sessionId)) {
            disconnect.increment();
        }
    }
}
