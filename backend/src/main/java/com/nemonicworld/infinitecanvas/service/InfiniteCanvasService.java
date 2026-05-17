package com.nemonicworld.infinitecanvas.service;

import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasColorUpdateRequest;
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
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;

public interface InfiniteCanvasService {

    InfiniteCanvasCreateResponse createCanvas(String userUuidValue, InfiniteCanvasCreateRequest request);

    InfiniteCanvasStateResponse getCanvasState(String userUuidValue, String roomCode);

    InfiniteCanvasStateResponse connectCanvas(String userUuidValue, String roomCode);

    InfiniteCanvasStateResponse disconnectCanvas(String userUuidValue, String roomCode);

    InfiniteCanvasOpsAppliedResponse applyOperations(String userUuidValue, String roomCode,
        InfiniteCanvasOpsRequest request);

    InfiniteCanvasStateResponse replaceSnapshot(String userUuidValue, String roomCode,
        InfiniteCanvasSnapshotRequest request);

    InfiniteCanvasCursorResponse updateCursor(String userUuidValue, String roomCode,
        InfiniteCanvasCursorRequest request);

    InfiniteCanvasLockResponse acquireLock(String userUuidValue, String roomCode, InfiniteCanvasLockRequest request);

    InfiniteCanvasLockResponse releaseLock(String userUuidValue, String roomCode, InfiniteCanvasLockRequest request);

    InfiniteCanvasLeaveResponse leaveCanvas(String userUuidValue, String roomCode);

    InfiniteCanvasParticipantResponse updateMyColor(String userUuidValue, String roomCode,
        InfiniteCanvasColorUpdateRequest request);

    InfiniteCanvasOutputSaveResponse saveOutput(String userUuidValue, String roomCode,
        InfiniteCanvasOutputSaveRequest request);
}
