package com.nemonicworld.backoffice.relay.service.room;

import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.relay.dto.response.BackofficeRelayRoomDeleteResponse;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.service.close.RelayRoomCloseCommand;
import com.nemonicworld.relay.service.close.RelayRoomCloseResult;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class BackofficeRelayRoomCommandUseCase {

    private static final String ROOM_ALREADY_CLOSED_MESSAGE = "이미 종료된 방입니다.";

    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomCloseCommand relayRoomCloseCommand;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final AdminAuditLogger adminAuditLogger;

    public BackofficeRelayRoomCommandUseCase(RelayRoomPolicy relayRoomPolicy,
        RelayRoomCloseCommand relayRoomCloseCommand, RelayRoomEventPublisher relayRoomEventPublisher,
        AdminAuditLogger adminAuditLogger) {
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomCloseCommand = relayRoomCloseCommand;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.adminAuditLogger = adminAuditLogger;
    }

    public BackofficeRelayRoomDeleteResponse deleteActiveRelayRoom(AdminPrincipal adminPrincipal, String roomCode,
        AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);
        relayRoomPolicy.validateRoomCode(roomCode);
        LocalDateTime closedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCode);
            if (roomState.status() == RelayRoomStatus.CLOSED) {
                throw new ConflictException(ROOM_ALREADY_CLOSED_MESSAGE);
            }

            RelayRoomCloseResult closeResult = relayRoomCloseCommand.closeActiveRoomIfUnchanged(roomState, closedAt);
            if (closeResult.closed()) {
                RelayRoomEventLogger.apiBusiness("relay_room_closed",
                    metadata("room_id", closeResult.roomCode(), "close_reason", "admin_force", "room_status_before",
                        roomState.status(), "participant_count", roomState.participantCount()));
                relayRoomEventPublisher.publishRoomClosed(closeResult.roomCode(), closeResult.closedAt(),
                    "admin_force");
                adminAuditLogger.logRelayRoomForceClose(adminPrincipal, closeResult.roomCode(),
                    roomState.status().name(), clientInfo);
                return new BackofficeRelayRoomDeleteResponse(closeResult.roomCode());
            }
        }

        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }
}
