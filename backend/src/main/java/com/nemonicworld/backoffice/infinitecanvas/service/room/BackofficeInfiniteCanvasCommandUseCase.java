package com.nemonicworld.backoffice.infinitecanvas.service.room;

import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasCloseResponse;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasInviteMetadataSyncService;
import com.nemonicworld.infinitecanvas.websocket.InfiniteCanvasEventPublisher;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BackofficeInfiniteCanvasCommandUseCase {

    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";
    private static final String CANVAS_ALREADY_CLOSED_MESSAGE = "이미 종료된 캔버스입니다.";
    private static final String CANVAS_UPDATE_CONFLICT_MESSAGE = "무한 캔버스 상태 갱신 충돌이 발생했습니다. 다시 시도해주세요.";

    private static final int CANVAS_UPDATE_MAX_RETRIES = 8;

    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService;
    private final InfiniteCanvasEventPublisher infiniteCanvasEventPublisher;
    private final AdminAuditLogger adminAuditLogger;
    private final RoomCodeGenerator roomCodeGenerator;

    public BackofficeInfiniteCanvasCommandUseCase(InfiniteCanvasRepository infiniteCanvasRepository,
        InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService,
        InfiniteCanvasEventPublisher infiniteCanvasEventPublisher, AdminAuditLogger adminAuditLogger,
        RoomCodeGenerator roomCodeGenerator) {
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.infiniteCanvasInviteMetadataSyncService = infiniteCanvasInviteMetadataSyncService;
        this.infiniteCanvasEventPublisher = infiniteCanvasEventPublisher;
        this.adminAuditLogger = adminAuditLogger;
        this.roomCodeGenerator = roomCodeGenerator;
    }

    public BackofficeInfiniteCanvasCloseResponse closeActiveCanvas(AdminPrincipal adminPrincipal, String roomCode,
        AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);
        String normalizedRoomCode = normalizeRoomCode(roomCode);
        LocalDateTime closedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        for (int attempt = 0; attempt < CANVAS_UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = infiniteCanvasRepository.findByRoomCode(normalizedRoomCode)
                .orElseThrow(() -> new NotFoundException(CANVAS_NOT_FOUND_MESSAGE));
            if (state.status() == InfiniteCanvasStatus.CLOSED) {
                throw new ConflictException(CANVAS_ALREADY_CLOSED_MESSAGE);
            }

            InfiniteCanvasState closedState = closeState(state, closedAt);
            if (infiniteCanvasRepository.saveIfUnchanged(state, closedState)) {
                infiniteCanvasInviteMetadataSyncService.syncWithCanvasState(closedState);
                StructuredEventLogger.apiBusiness("infinite_canvas_closed", "infinite_canvas", state.hostUserUuid(),
                    StructuredEventLogger.metadata("room_code", closedState.roomCode(), "close_reason", "admin_force",
                        "canvas_status_before", state.status(), "participant_count", state.participantCount(),
                        "connected_participant_count", state.connectedParticipantCount()));
                infiniteCanvasEventPublisher.publishCanvasClosed(closedState.roomCode(), closedAt);
                adminAuditLogger.logInfiniteCanvasForceClose(adminPrincipal, closedState.roomCode(),
                    state.status().name(), clientInfo);
                return new BackofficeInfiniteCanvasCloseResponse(closedState.roomCode());
            }
        }

        throw new ConflictException(CANVAS_UPDATE_CONFLICT_MESSAGE);
    }

    private String normalizeRoomCode(String roomCode) {
        if (!StringUtils.hasText(roomCode) || !roomCodeGenerator.isValid(roomCode.trim())) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }

        return roomCode.trim();
    }

    private InfiniteCanvasState closeState(InfiniteCanvasState state, LocalDateTime closedAt) {
        return new InfiniteCanvasState(state.roomCode(), InfiniteCanvasStatus.CLOSED, state.hostUserUuid(),
            state.participants(), state.elements(), state.operations(), Map.of(), Map.of(), state.viewport(),
            state.maxParticipants(), state.revision(), state.createdAt(), closedAt, closedAt);
    }
}
