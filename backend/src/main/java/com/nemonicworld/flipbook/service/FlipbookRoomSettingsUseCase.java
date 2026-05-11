package com.nemonicworld.flipbook.service;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerResponse;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

/**
 * 플립북 방 설정 변경 유스케이스입니다.
 */
@Service
@RequiredArgsConstructor
public class FlipbookRoomSettingsUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookRoomViewerFactory flipbookRoomViewerFactory;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    /**
     * 대기 중인 플립북 방의 라운드별 제한 시간을 변경합니다.
     */
    @Transactional(readOnly = true)
    public FlipbookRoomStateResponse updateRoomSettings(String userUuidValue, String roomCodeValue,
        FlipbookRoomSettingsRequest request) {
        int timeLimitSeconds = flipbookRoomPolicy.resolveTimeLimitSeconds(request);
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();

        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
            flipbookRoomPolicy.validateWaitingRoomForSettings(roomState);
            FlipbookRoomParticipant participant = flipbookRoomPolicy.requireParticipant(roomState, viewerUserUuid);
            flipbookRoomPolicy.validateRoomHost(viewerUserUuid, roomState, participant);

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            FlipbookRoomState updatedRoomState = roomState.withTimeLimitSeconds(timeLimitSeconds, now);

            if (flipbookRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                flipbookInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
                FlipbookRoomEventLogger.apiBusiness("flipbook_room_settings_changed",
                    metadata("room_id", updatedRoomState.roomCode(), "uuid", viewerUserUuid,
                        "time_limit_seconds_before", roomState.timeLimitSeconds(), "time_limit_seconds_after",
                        updatedRoomState.timeLimitSeconds(), "room_status", updatedRoomState.status()));
                FlipbookRoomViewerResponse viewer = flipbookRoomViewerFactory.create(viewerUserUuid, updatedRoomState);

                return FlipbookRoomStateResponse.from(updatedRoomState, viewer);
            }
        }

        throw new ConflictException(FlipbookRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }
}
