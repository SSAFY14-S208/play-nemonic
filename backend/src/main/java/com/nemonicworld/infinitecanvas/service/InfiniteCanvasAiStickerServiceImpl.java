package com.nemonicworld.infinitecanvas.service;

import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasAiStickerCreateRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasAiStickerCreateResponse;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasAiStickerCreateUseCase;
import org.springframework.stereotype.Service;

@Service
public class InfiniteCanvasAiStickerServiceImpl implements InfiniteCanvasAiStickerService {

    private final InfiniteCanvasAiStickerCreateUseCase infiniteCanvasAiStickerCreateUseCase;

    public InfiniteCanvasAiStickerServiceImpl(
        InfiniteCanvasAiStickerCreateUseCase infiniteCanvasAiStickerCreateUseCase) {
        this.infiniteCanvasAiStickerCreateUseCase = infiniteCanvasAiStickerCreateUseCase;
    }

    @Override
    public InfiniteCanvasAiStickerCreateResponse createSticker(String userUuidValue, String roomCode,
        InfiniteCanvasAiStickerCreateRequest request) {
        return infiniteCanvasAiStickerCreateUseCase.createSticker(userUuidValue, roomCode, request);
    }
}
