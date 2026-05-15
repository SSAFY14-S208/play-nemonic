package com.nemonicworld.infinitecanvas.service;

import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCursorRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOpsRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasParticipantUpdateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasSnapshotRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCursorResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLeaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOpsAppliedResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;

public interface InfiniteCanvasService {

    InfiniteCanvasStateResponse createCanvas(String userUuidValue, InfiniteCanvasCreateRequest request);

    InfiniteCanvasStateResponse getCanvas(String userUuidValue, String canvasId);

    InfiniteCanvasStateResponse connectCanvas(String userUuidValue, String canvasId);

    InfiniteCanvasStateResponse disconnectCanvas(String userUuidValue, String canvasId);

    InfiniteCanvasParticipantResponse updateMyParticipant(String userUuidValue, String canvasId,
        InfiniteCanvasParticipantUpdateRequest request);

    InfiniteCanvasOpsAppliedResponse applyOperations(String userUuidValue, String canvasId,
        InfiniteCanvasOpsRequest request);

    InfiniteCanvasStateResponse replaceSnapshot(String userUuidValue, String canvasId,
        InfiniteCanvasSnapshotRequest request);

    InfiniteCanvasCursorResponse updateCursor(String userUuidValue, String canvasId,
        InfiniteCanvasCursorRequest request);

    InfiniteCanvasLeaveResponse leaveCanvas(String userUuidValue, String canvasId);
}
