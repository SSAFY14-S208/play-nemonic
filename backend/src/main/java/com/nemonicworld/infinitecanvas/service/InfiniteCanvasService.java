package com.nemonicworld.infinitecanvas.service;

import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCursorRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasLockRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOpsRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOutputSaveRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasSnapshotRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCursorResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCreateResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLeaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLockResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOpsAppliedResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOutputSaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;

public interface InfiniteCanvasService {

    InfiniteCanvasCreateResponse createCanvas(String userUuidValue, InfiniteCanvasCreateRequest request);

    InfiniteCanvasStateResponse connectCanvas(String userUuidValue, String canvasId);

    InfiniteCanvasStateResponse disconnectCanvas(String userUuidValue, String canvasId);

    InfiniteCanvasOpsAppliedResponse applyOperations(String userUuidValue, String canvasId,
        InfiniteCanvasOpsRequest request);

    InfiniteCanvasStateResponse replaceSnapshot(String userUuidValue, String canvasId,
        InfiniteCanvasSnapshotRequest request);

    InfiniteCanvasCursorResponse updateCursor(String userUuidValue, String canvasId,
        InfiniteCanvasCursorRequest request);

    InfiniteCanvasLockResponse acquireLock(String userUuidValue, String canvasId, InfiniteCanvasLockRequest request);

    InfiniteCanvasLockResponse releaseLock(String userUuidValue, String canvasId, InfiniteCanvasLockRequest request);

    InfiniteCanvasLeaveResponse leaveCanvas(String userUuidValue, String canvasId);

    InfiniteCanvasOutputSaveResponse saveOutput(String userUuidValue, String canvasId,
        InfiniteCanvasOutputSaveRequest request);
}
