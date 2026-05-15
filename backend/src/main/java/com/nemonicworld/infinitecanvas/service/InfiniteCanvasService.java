package com.nemonicworld.infinitecanvas.service;

import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasParticipantUpdateRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLeaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;

public interface InfiniteCanvasService {

    InfiniteCanvasStateResponse createCanvas(String userUuidValue, InfiniteCanvasCreateRequest request);

    InfiniteCanvasStateResponse getCanvas(String userUuidValue, String canvasId);

    InfiniteCanvasStateResponse connectCanvas(String userUuidValue, String canvasId);

    InfiniteCanvasParticipantResponse updateMyParticipant(String userUuidValue, String canvasId,
        InfiniteCanvasParticipantUpdateRequest request);

    InfiniteCanvasLeaveResponse leaveCanvas(String userUuidValue, String canvasId);
}
