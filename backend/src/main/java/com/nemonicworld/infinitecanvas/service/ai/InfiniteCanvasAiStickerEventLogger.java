package com.nemonicworld.infinitecanvas.service.ai;

import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InfiniteCanvasAiStickerEventLogger {

    public void logRequested(String userUuid, String roomCode, UUID stickerId, String style, String promptVersion) {
        StructuredEventLogger.apiBusiness("infinite_canvas_ai_sticker_requested", "infinite_canvas", userUuid,
            StructuredEventLogger.metadata("room_id", roomCode, "sticker_id", stickerId, "style", style,
                "prompt_version", promptVersion, "result", "requested"));
    }

    public void logCreated(String userUuid, String roomCode, UUID stickerId, String style, String promptVersion,
        int byteSize, long latencyMs) {
        StructuredEventLogger.apiBusiness("infinite_canvas_ai_sticker_created", "infinite_canvas", userUuid,
            StructuredEventLogger.metadata("room_id", roomCode, "sticker_id", stickerId, "style", style,
                "prompt_version", promptVersion, "byte_size", byteSize, "gms_latency_ms", latencyMs, "result",
                "success"));
    }

    public void logFailed(String userUuid, String roomCode, UUID stickerId, String style, String promptVersion,
        long latencyMs, RuntimeException error) {
        StructuredEventLogger.apiBusinessWarn("infinite_canvas_ai_sticker_failed", "infinite_canvas", userUuid,
            "AI 스티커 생성에 실패했습니다.",
            StructuredEventLogger.metadata("room_id", roomCode, "sticker_id", stickerId, "style", style,
                "prompt_version", promptVersion, "gms_latency_ms", latencyMs, "result", "failed", "reason_code",
                error.getClass().getSimpleName()),
            error);
    }
}
