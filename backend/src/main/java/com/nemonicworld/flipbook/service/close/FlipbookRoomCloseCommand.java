package com.nemonicworld.flipbook.service.close;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.FlipbookInviteMetadataSyncService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * FINISHED 플립북 방을 Redis CAS 방식으로 CLOSED 상태로 바꿉니다.
 */
@Component
public class FlipbookRoomCloseCommand {

    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    public FlipbookRoomCloseCommand(FlipbookRoomRepository flipbookRoomRepository,
        FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService) {
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookInviteMetadataSyncService = flipbookInviteMetadataSyncService;
    }

    /**
     * 이벤트 발행 없이 방 상태만 CLOSED로 저장합니다.
     */
    public FlipbookRoomCloseResult closeFinishedRoomIfUnchanged(FlipbookRoomState roomState, LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        if (roomState == null || roomState.status() != FlipbookRoomStatus.FINISHED) {
            return FlipbookRoomCloseResult.noOp(roomState == null ? null : roomState.roomCode());
        }

        FlipbookRoomState closedRoomState = roomState.close(closedAt);
        if (!flipbookRoomRepository.saveIfUnchanged(roomState, closedRoomState)) {
            return FlipbookRoomCloseResult.noOp(roomState.roomCode());
        }
        flipbookInviteMetadataSyncService.syncWithRoomState(closedRoomState);

        return FlipbookRoomCloseResult.closed(closedRoomState, closedAt);
    }
}
