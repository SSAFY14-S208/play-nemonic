package com.nemonicworld.infinitecanvas.service;

import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasAiStickerCreateRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasAiStickerCreateResponse;

public interface InfiniteCanvasAiStickerService {

    InfiniteCanvasAiStickerCreateResponse createSticker(String userUuidValue, String roomCode,
        InfiniteCanvasAiStickerCreateRequest request);
}
