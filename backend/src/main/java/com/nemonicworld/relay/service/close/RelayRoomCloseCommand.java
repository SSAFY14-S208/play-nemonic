package com.nemonicworld.relay.service.close;

import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * FINISHED 방을 Redis CAS 방식으로 CLOSED 상태로 바꿉니다.
 */
@Component
public class RelayRoomCloseCommand {

    private final RelayRoomRepository relayRoomRepository;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;

    public RelayRoomCloseCommand(RelayRoomRepository relayRoomRepository,
        RelayInviteMetadataSyncService relayInviteMetadataSyncService) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
    }

    /**
     * 이벤트 발행 없이 방 상태만 CLOSED로 저장합니다.
     */
    public RelayRoomCloseResult closeFinishedRoomIfUnchanged(RelayRoomState roomState, LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        if (roomState == null || roomState.status() != RelayRoomStatus.FINISHED) {
            return RelayRoomCloseResult.noOp(roomState == null ? null : roomState.roomCode());
        }

        RelayRoomState closedRoomState = roomState.close(closedAt);
        if (!relayRoomRepository.saveIfUnchanged(roomState, closedRoomState)) {
            return RelayRoomCloseResult.noOp(roomState.roomCode());
        }
        relayInviteMetadataSyncService.syncWithRoomState(closedRoomState);

        return RelayRoomCloseResult.closed(closedRoomState, closedAt);
    }
}
