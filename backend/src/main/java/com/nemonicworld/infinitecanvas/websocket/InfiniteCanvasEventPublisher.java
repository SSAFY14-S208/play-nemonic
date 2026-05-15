package com.nemonicworld.infinitecanvas.websocket;

import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasEventResponse;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasEventType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class InfiniteCanvasEventPublisher {

    private static final String CANVAS_TOPIC_PREFIX = "/topic/infinite-canvas/canvases/";

    private final SimpMessagingTemplate messagingTemplate;

    public InfiniteCanvasEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishParticipantConnected(InfiniteCanvasStateResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.PARTICIPANT_CONNECTED, response.canvasId(), response);
    }

    private void publishCanvasEvent(InfiniteCanvasEventType type, String canvasId, Object data) {
        InfiniteCanvasEventResponse event = InfiniteCanvasEventResponse.of(type, canvasId, data);

        messagingTemplate.convertAndSend(CANVAS_TOPIC_PREFIX + canvasId, event);
    }
}
