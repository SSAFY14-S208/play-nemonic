package com.nemonicworld.flipbook.service;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.service.support.FlipbookRoomTimeLimitSettings;
import com.nemonicworld.flipbook.service.support.FlipbookRuntimeSettingsProvider;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플립북 방 상태 조회 유스케이스입니다.
 */
@Service
@RequiredArgsConstructor
public class FlipbookRoomQueryUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookRoomViewerFactory flipbookRoomViewerFactory;
    private final FlipbookRuntimeSettingsProvider flipbookRuntimeSettingsProvider;

    /**
     * Redis에 저장된 플립북 방 상태를 변경하지 않고 조회합니다.
     */
    @Transactional(readOnly = true)
    public FlipbookRoomStateResponse getRoomState(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);

        FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
        FlipbookRoomViewerResponse viewer = flipbookRoomViewerFactory.create(viewerUser.getId().toString(), roomState);
        FlipbookRoomTimeLimitSettings timeLimitSettings = flipbookRuntimeSettingsProvider
            .currentRoomTimeLimitSettings();

        return FlipbookRoomStateResponse.from(roomState, viewer, timeLimitSettings);
    }
}
