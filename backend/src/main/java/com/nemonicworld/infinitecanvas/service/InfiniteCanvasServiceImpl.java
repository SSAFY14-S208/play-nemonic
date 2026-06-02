package com.nemonicworld.infinitecanvas.service;

import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasColorUpdateRequest;
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
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.service.canvas.InfiniteCanvasEditingUseCase;
import com.nemonicworld.infinitecanvas.service.lock.InfiniteCanvasLockUseCase;
import com.nemonicworld.infinitecanvas.service.output.InfiniteCanvasOutputSaveUseCase;
import com.nemonicworld.infinitecanvas.service.participant.InfiniteCanvasLeaveUseCase;
import com.nemonicworld.infinitecanvas.service.participant.InfiniteCanvasParticipantProfileUseCase;
import com.nemonicworld.infinitecanvas.service.room.InfiniteCanvasConnectionUseCase;
import com.nemonicworld.infinitecanvas.service.room.InfiniteCanvasRoomCreateUseCase;
import com.nemonicworld.infinitecanvas.service.room.InfiniteCanvasRoomQueryUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InfiniteCanvasServiceImpl implements InfiniteCanvasService {

    private final InfiniteCanvasEditingUseCase infiniteCanvasEditingUseCase;
    private final InfiniteCanvasLockUseCase infiniteCanvasLockUseCase;
    private final InfiniteCanvasConnectionUseCase infiniteCanvasConnectionUseCase;
    private final InfiniteCanvasRoomCreateUseCase infiniteCanvasRoomCreateUseCase;
    private final InfiniteCanvasRoomQueryUseCase infiniteCanvasRoomQueryUseCase;
    private final InfiniteCanvasLeaveUseCase infiniteCanvasLeaveUseCase;
    private final InfiniteCanvasParticipantProfileUseCase infiniteCanvasParticipantProfileUseCase;
    private final InfiniteCanvasOutputSaveUseCase infiniteCanvasOutputSaveUseCase;

    public InfiniteCanvasServiceImpl(InfiniteCanvasEditingUseCase infiniteCanvasEditingUseCase,
        InfiniteCanvasLockUseCase infiniteCanvasLockUseCase,
        InfiniteCanvasConnectionUseCase infiniteCanvasConnectionUseCase,
        InfiniteCanvasRoomCreateUseCase infiniteCanvasRoomCreateUseCase,
        InfiniteCanvasRoomQueryUseCase infiniteCanvasRoomQueryUseCase,
        InfiniteCanvasLeaveUseCase infiniteCanvasLeaveUseCase,
        InfiniteCanvasParticipantProfileUseCase infiniteCanvasParticipantProfileUseCase,
        InfiniteCanvasOutputSaveUseCase infiniteCanvasOutputSaveUseCase) {
        this.infiniteCanvasEditingUseCase = infiniteCanvasEditingUseCase;
        this.infiniteCanvasLockUseCase = infiniteCanvasLockUseCase;
        this.infiniteCanvasConnectionUseCase = infiniteCanvasConnectionUseCase;
        this.infiniteCanvasRoomCreateUseCase = infiniteCanvasRoomCreateUseCase;
        this.infiniteCanvasRoomQueryUseCase = infiniteCanvasRoomQueryUseCase;
        this.infiniteCanvasLeaveUseCase = infiniteCanvasLeaveUseCase;
        this.infiniteCanvasParticipantProfileUseCase = infiniteCanvasParticipantProfileUseCase;
        this.infiniteCanvasOutputSaveUseCase = infiniteCanvasOutputSaveUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasCreateResponse createCanvas(String userUuidValue, InfiniteCanvasCreateRequest request) {
        return infiniteCanvasRoomCreateUseCase.createCanvas(userUuidValue, request);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse getCanvasState(String userUuidValue, String roomCode) {
        return infiniteCanvasRoomQueryUseCase.getCanvasState(userUuidValue, roomCode);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasLockResponse acquireLock(String userUuidValue, String roomCode,
        InfiniteCanvasLockRequest request) {
        return infiniteCanvasLockUseCase.acquireLock(userUuidValue, roomCode, request);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasLockResponse releaseLock(String userUuidValue, String roomCode,
        InfiniteCanvasLockRequest request) {
        return infiniteCanvasLockUseCase.releaseLock(userUuidValue, roomCode, request);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse replaceSnapshot(String userUuidValue, String roomCode,
        InfiniteCanvasSnapshotRequest request) {
        return infiniteCanvasEditingUseCase.replaceSnapshot(userUuidValue, roomCode, request);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasCursorResponse updateCursor(String userUuidValue, String roomCode,
        InfiniteCanvasCursorRequest request) {
        return infiniteCanvasEditingUseCase.updateCursor(userUuidValue, roomCode, request);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse connectCanvas(String userUuidValue, String roomCode) {
        return infiniteCanvasConnectionUseCase.connectCanvas(userUuidValue, roomCode);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse disconnectCanvas(String userUuidValue, String roomCode) {
        return infiniteCanvasConnectionUseCase.disconnectCanvas(userUuidValue, roomCode);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasOpsAppliedResponse applyOperations(String userUuidValue, String roomCode,
        InfiniteCanvasOpsRequest request) {
        return infiniteCanvasEditingUseCase.applyOperations(userUuidValue, roomCode, request);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasLeaveResponse leaveCanvas(String userUuidValue, String roomCode) {
        return infiniteCanvasLeaveUseCase.leaveCanvas(userUuidValue, roomCode);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasParticipantResponse updateMyColor(String userUuidValue, String roomCode,
        InfiniteCanvasColorUpdateRequest request) {
        return infiniteCanvasParticipantProfileUseCase.updateMyColor(userUuidValue, roomCode, request);
    }

    @Override
    @Transactional
    public InfiniteCanvasOutputSaveResponse saveOutput(String userUuidValue, String roomCode,
        InfiniteCanvasOutputSaveRequest request) {
        return infiniteCanvasOutputSaveUseCase.saveOutput(userUuidValue, roomCode, request);
    }
}
