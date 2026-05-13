package com.nemonicworld.flipbook.service.game;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerResponse;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.support.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.service.support.FlipbookRoomPolicy;
import com.nemonicworld.flipbook.service.support.FlipbookRoomViewerFactory;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

/**
 * 플립북 게임 시작 유스케이스입니다.
 */
@Service
@RequiredArgsConstructor
public class FlipbookRoomStartUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookRoomViewerFactory flipbookRoomViewerFactory;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    /**
     * 방장이 대기 중인 플립북 방을 게임 진행 상태로 전환합니다.
     */
    @Transactional(readOnly = true)
    public FlipbookRoomStateResponse startRoom(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();

        try {
            for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
                FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
                FlipbookRoomParticipant participant = flipbookRoomPolicy.requireParticipant(roomState, viewerUserUuid);
                flipbookRoomPolicy.validateRoomHost(viewerUserUuid, roomState, participant);
                flipbookRoomPolicy.validateStartableRoomStatus(roomState);

                List<FlipbookRoomParticipant> startParticipants = flipbookRoomPolicy.findStartParticipants(roomState);
                int totalRounds = flipbookRoomPolicy.resolveDefaultTotalRounds();
                List<FlipbookFrameAssignment> assignments = FlipbookFrameAssignmentGenerator.generate(startParticipants,
                    totalRounds);
                LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
                FlipbookRoomState updatedRoomState = roomState.startGame(totalRounds, assignments, now);

                if (flipbookRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                    flipbookInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
                    FlipbookRoomEventLogger.apiBusiness("flipbook_game_started",
                        metadata("room_id", updatedRoomState.roomCode(), "host_uuid", viewerUserUuid,
                            "participant_count", updatedRoomState.participantCount(), "total_rounds",
                            updatedRoomState.totalRounds(), "time_limit_seconds", updatedRoomState.timeLimitSeconds(),
                            "round", updatedRoomState.currentRound()));
                    FlipbookRoomEventLogger.apiBusiness("flipbook_round_started",
                        metadata("room_id", updatedRoomState.roomCode(), "round", updatedRoomState.currentRound(),
                            "round_deadline_at", updatedRoomState.roundDeadlineAt()));
                    FlipbookRoomViewerResponse viewer = flipbookRoomViewerFactory.create(viewerUserUuid,
                        updatedRoomState);

                    return FlipbookRoomStateResponse.from(updatedRoomState, viewer);
                }
            }

            throw new ConflictException(FlipbookRoomPolicy.ROOM_START_UPDATE_CONFLICT_MESSAGE);
        } catch (RuntimeException e) {
            FlipbookRoomEventLogger.apiBusiness("flipbook_start_rejected", metadata("room_id", roomCodeValue, "uuid",
                viewerUserUuid, "result", "rejected", "reason_code", e.getClass().getSimpleName()));
            throw e;
        }
    }
}
