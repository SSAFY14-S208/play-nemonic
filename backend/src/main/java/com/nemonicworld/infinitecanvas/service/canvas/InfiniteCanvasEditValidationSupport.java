package com.nemonicworld.infinitecanvas.service.canvas;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCursorRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOperationRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOpsRequest;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InfiniteCanvasEditValidationSupport {

    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String NOT_PARTICIPANT_MESSAGE = "무한 캔버스 참여자가 아닙니다.";
    private static final String INVALID_CURSOR_MESSAGE = "커서 좌표 형식이 올바르지 않습니다.";
    private static final String INVALID_ELEMENTS_MESSAGE = "캔버스 요소 목록 형식이 올바르지 않습니다.";
    private static final String INVALID_OPERATIONS_MESSAGE = "캔버스 편집 연산 목록 형식이 올바르지 않습니다.";
    private static final String SNAPSHOT_HOST_MESSAGE = "캔버스 방장만 전체 스냅샷을 교체할 수 있습니다.";
    private static final int MAX_ELEMENTS = 5000;
    private static final int MAX_OPERATIONS_PER_MESSAGE = 100;

    private final RoomCodeGenerator roomCodeGenerator;

    public InfiniteCanvasEditValidationSupport(RoomCodeGenerator roomCodeGenerator) {
        this.roomCodeGenerator = roomCodeGenerator;
    }

    public String normalizeRoomCode(String roomCode) {
        if (!StringUtils.hasText(roomCode) || !roomCodeGenerator.isValid(roomCode.trim())) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }

        return roomCode.trim();
    }

    public void requireParticipant(InfiniteCanvasState state, String userUuid) {
        if (!state.hasParticipant(userUuid)) {
            throw new NotFoundException(NOT_PARTICIPANT_MESSAGE);
        }
    }

    public void requireCanvasHost(InfiniteCanvasState state, String userUuid) {
        if (!userUuid.equals(state.hostUserUuid())) {
            throw new ConflictException(SNAPSHOT_HOST_MESSAGE);
        }
    }

    public void validateCursorRequest(InfiniteCanvasCursorRequest request) {
        if (request == null || request.x() == null || request.y() == null || !isFinite(request.x())
            || !isFinite(request.y()) || (request.zoom() != null && !isFinite(request.zoom()))) {
            throw new BadRequestException(INVALID_CURSOR_MESSAGE);
        }
    }

    public List<JsonNode> normalizeElements(List<JsonNode> elements) {
        if (elements == null) {
            return List.of();
        }
        if (elements.size() > MAX_ELEMENTS) {
            throw new BadRequestException(INVALID_ELEMENTS_MESSAGE);
        }
        if (elements.stream().anyMatch(element -> element == null || element.isNull())) {
            throw new BadRequestException(INVALID_ELEMENTS_MESSAGE);
        }

        return List.copyOf(elements);
    }

    public List<InfiniteCanvasOperationRequest> normalizeOperationRequests(InfiniteCanvasOpsRequest request) {
        List<InfiniteCanvasOperationRequest> operations = request == null ? null : request.operations();
        if (operations == null || operations.isEmpty() || operations.size() > MAX_OPERATIONS_PER_MESSAGE
            || operations.stream().anyMatch(operation -> operation == null || operation.operationType() == null
                || !StringUtils.hasText(operation.clientOperationId()))) {
            throw new BadRequestException(INVALID_OPERATIONS_MESSAGE);
        }

        return List.copyOf(operations);
    }

    private boolean isFinite(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite();
    }
}
