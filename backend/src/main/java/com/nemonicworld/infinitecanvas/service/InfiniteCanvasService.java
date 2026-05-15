package com.nemonicworld.infinitecanvas.service;

import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;

public interface InfiniteCanvasService {

    InfiniteCanvasStateResponse createCanvas(String userUuidValue, InfiniteCanvasCreateRequest request);

    InfiniteCanvasStateResponse getCanvas(String userUuidValue, String canvasId);
}
