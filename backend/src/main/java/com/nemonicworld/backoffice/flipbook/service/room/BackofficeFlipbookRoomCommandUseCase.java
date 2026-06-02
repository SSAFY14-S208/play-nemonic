package com.nemonicworld.backoffice.flipbook.service.room;

import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.flipbook.dto.response.BackofficeFlipbookRoomDeleteResponse;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.support.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.service.support.FlipbookRoomPolicy;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class BackofficeFlipbookRoomCommandUseCase {

    private static final String ROOM_ALREADY_CLOSED_MESSAGE = "이미 종료된 방입니다.";

    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;
    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;
    private final AdminAuditLogger adminAuditLogger;

    public BackofficeFlipbookRoomCommandUseCase(FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRoomPolicy flipbookRoomPolicy, FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService,
        FlipbookRoomEventPublisher flipbookRoomEventPublisher, AdminAuditLogger adminAuditLogger) {
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomPolicy = flipbookRoomPolicy;
        this.flipbookInviteMetadataSyncService = flipbookInviteMetadataSyncService;
        this.flipbookRoomEventPublisher = flipbookRoomEventPublisher;
        this.adminAuditLogger = adminAuditLogger;
    }

    public BackofficeFlipbookRoomDeleteResponse deleteActiveFlipbookRoom(AdminPrincipal adminPrincipal, String roomCode,
        AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);
        flipbookRoomPolicy.validateRoomCode(roomCode);
        LocalDateTime closedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCode);
            if (roomState.status() == FlipbookRoomStatus.CLOSED) {
                throw new ConflictException(ROOM_ALREADY_CLOSED_MESSAGE);
            }

            FlipbookRoomState closedRoomState = roomState.close(closedAt);
            if (flipbookRoomRepository.saveIfUnchanged(roomState, closedRoomState)) {
                flipbookInviteMetadataSyncService.syncWithRoomState(closedRoomState);
                FlipbookRoomEventLogger.apiBusiness("flipbook_room_closed",
                    metadata("room_id", closedRoomState.roomCode(), "close_reason", "admin_force", "room_status_before",
                        roomState.status(), "participant_count", roomState.participantCount(), "closed_at",
                        closedRoomState.updatedAt()));
                flipbookRoomEventPublisher.publishRoomClosed(closedRoomState.roomCode(), closedRoomState.updatedAt());
                adminAuditLogger.logFlipbookRoomForceClose(adminPrincipal, closedRoomState.roomCode(),
                    roomState.status().name(), clientInfo);
                return new BackofficeFlipbookRoomDeleteResponse(closedRoomState.roomCode());
            }
        }

        throw new ConflictException(FlipbookRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }
}
