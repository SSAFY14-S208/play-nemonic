package com.nemonicworld.infinitecanvas.service.ai;

public record InfiniteCanvasAiStickerGmsRequest(String systemPrompt, String userPrompt, String style, int width,
    int height, boolean transparentBackground) {
}
