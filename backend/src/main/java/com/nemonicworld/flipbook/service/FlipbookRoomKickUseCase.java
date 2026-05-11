package com.nemonicworld.flipbook.service;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomKickResponse;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

/**
 * 플립북 대기실 참여자 강퇴 유스케이스입니다.
 */
@Service
@RequiredArgsConstructor
public class FlipbookRoomKickUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    /**
     * 방장이 WAITING 상태의 플립북 방에서 일반 참여자를 강퇴합니다.
     */
    @Transactional(readOnly = true)
    public FlipbookRoomKickResponse kickParticipant(String userUuidValue, String roomCodeValue,
        String targetUserUuidValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        UUID targetUserUuid = anonymousUserResolver.parseUuid(targetUserUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();
        String targetUserUuidString = targetUserUuid.toString();

        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
            // 요청한 참여자 정보 조사
            FlipbookRoomParticipant viewerParticipant = flipbookRoomPolicy.requireParticipant(roomState,
                viewerUserUuid);
            // 방장인지 확인
            flipbookRoomPolicy.validateKickHost(viewerUserUuid, roomState, viewerParticipant);
            // 대기방이 맞는지
            flipbookRoomPolicy.validateWaitingRoomForKick(roomState);
            // 강퇴 대상 참여자 정보 조사
            FlipbookRoomParticipant targetParticipant = flipbookRoomPolicy.requireKickTargetParticipant(roomState,
                targetUserUuidString);
            // 강퇴 허용 대상인지
            flipbookRoomPolicy.validateKickTarget(viewerUserUuid, roomState, targetParticipant);

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            FlipbookRoomState updatedRoomState = kickParticipant(roomState, targetParticipant, now);

            if (flipbookRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                flipbookInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
                FlipbookRoomEventLogger.apiBusiness("flipbook_participant_kicked",
                    metadata("room_id", updatedRoomState.roomCode(), "host_uuid", viewerUserUuid, "target_uuid",
                        targetParticipant.userUuid(), "participant_count", updatedRoomState.participantCount()));
                return new FlipbookRoomKickResponse(updatedRoomState.roomCode(), targetParticipant.userUuid(),
                    targetParticipant.nickname(), updatedRoomState.participantCount(), now);
            }
        }

        throw new ConflictException(FlipbookRoomPolicy.ROOM_KICK_UPDATE_CONFLICT_MESSAGE);
    }

    private FlipbookRoomState kickParticipant(FlipbookRoomState roomState, FlipbookRoomParticipant targetParticipant,
        LocalDateTime kickedAt) {
        // 이 참여자의 UUID가 강퇴 대상 UUID와 같지 않으면 남긴다
        List<FlipbookRoomParticipant> participants = roomState.participants().stream()
            .filter(participant -> !participant.userUuid().equals(targetParticipant.userUuid())).toList();
        List<String> kickedUserUuids = new ArrayList<>(roomState.kickedUserUuids());

        if (!kickedUserUuids.contains(targetParticipant.userUuid())) {
            kickedUserUuids.add(targetParticipant.userUuid());
        }

        return roomState.withParticipantsAndKickedUserUuids(participants, kickedUserUuids, kickedAt);
    }
}
