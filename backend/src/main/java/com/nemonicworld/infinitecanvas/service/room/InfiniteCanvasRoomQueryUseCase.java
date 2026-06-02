package com.nemonicworld.infinitecanvas.service.room;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasRoomQueryUseCase {

    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final InfiniteCanvasRepository infiniteCanvasRepository;

    public InfiniteCanvasRoomQueryUseCase(AnonymousUserResolver anonymousUserResolver,
        RoomCodeGenerator roomCodeGenerator, InfiniteCanvasRepository infiniteCanvasRepository) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse getCanvasState(String userUuidValue, String roomCode) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = normalizeRoomCode(roomCode);
        InfiniteCanvasState state = findActiveState(normalizedRoomCode);

        return InfiniteCanvasStateResponse.from(state, userUuid);
    }

    private String normalizeRoomCode(String roomCode) {
        if (!StringUtils.hasText(roomCode) || !roomCodeGenerator.isValid(roomCode.trim())) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }

        return roomCode.trim();
    }

    private InfiniteCanvasState findActiveState(String roomCode) {
        InfiniteCanvasState state = infiniteCanvasRepository.findByRoomCode(roomCode)
            .orElseThrow(() -> new NotFoundException(CANVAS_NOT_FOUND_MESSAGE));
        if (!state.isActive()) {
            throw new NotFoundException(CANVAS_NOT_FOUND_MESSAGE);
        }

        return state;
    }
}
