package com.nemonicworld.flipbook.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerBlockedReason;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 플립북 방 유스케이스들이 공유하는 기본 정책입니다.
 */
@Component
public class FlipbookRoomPolicy {

    static final int DEFAULT_TIME_LIMIT_SECONDS = 45;
    static final int MIN_PARTICIPANTS = 2;
    static final int MAX_PARTICIPANTS = 6;
    static final int HOST_JOIN_ORDER = 0;

    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String ROOM_NOT_FOUND_MESSAGE = "존재하지 않는 방입니다.";

    private final RoomCodeGenerator roomCodeGenerator;
    private final FlipbookRoomRepository flipbookRoomRepository;

    public FlipbookRoomPolicy(RoomCodeGenerator roomCodeGenerator, FlipbookRoomRepository flipbookRoomRepository) {
        this.roomCodeGenerator = roomCodeGenerator;
        this.flipbookRoomRepository = flipbookRoomRepository;
    }

    /**
     * 기본 닉네임인 '익명' 상태로는 협동 방을 만들 수 없도록 검증합니다.
     */
    void validateNicknameRegistered(AppUser appUser) {
        if (!StringUtils.hasText(appUser.getNickname()) || AppUser.ANONYMOUS_NICKNAME.equals(appUser.getNickname())) {
            throw new BadRequestException(NICKNAME_REQUIRED_MESSAGE);
        }
    }

    /**
     * 방코드는 Redis 조회 전에 공통 방코드 생성 규칙과 같은 형식인지 먼저 검증합니다.
     */
    void validateRoomCode(String roomCodeValue) {
        if (!roomCodeGenerator.isValid(roomCodeValue)) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }
    }

    /**
     * Redis에 저장된 플립북 방 상태를 조회하고, 없으면 공통 404 응답으로 변환합니다.
     */
    FlipbookRoomState findRoomState(String roomCodeValue) {
        return flipbookRoomRepository.findByRoomCode(roomCodeValue)
            .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MESSAGE));
    }

    /**
     * 현재 방 상태에서 특정 사용자의 참여자 정보를 찾습니다.
     */
    Optional<FlipbookRoomParticipant> findParticipant(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .findFirst();
    }

    /**
     * 비참여자가 지금 플립북 방에 신규 입장할 수 없는 이유를 계산합니다.
     */
    FlipbookRoomViewerBlockedReason findJoinBlockedReason(FlipbookRoomState roomState) {
        if (roomState.status() == FlipbookRoomStatus.WAITING) {
            if (roomState.participantCount() >= roomState.maxParticipants()) {
                return FlipbookRoomViewerBlockedReason.ROOM_FULL;
            }

            return null;
        }

        if (roomState.status() == FlipbookRoomStatus.PLAYING) {
            return FlipbookRoomViewerBlockedReason.GAME_IN_PROGRESS;
        }

        if (roomState.status() == FlipbookRoomStatus.FINISHED) {
            return FlipbookRoomViewerBlockedReason.ROOM_FINISHED;
        }

        return FlipbookRoomViewerBlockedReason.ROOM_CLOSED;
    }

    /**
     * 방장이 대기 중 방을 시작할 수 있는 상태인지 계산합니다.
     */
    boolean canStart(FlipbookRoomState roomState, boolean host) {
        return host && roomState.status() == FlipbookRoomStatus.WAITING
            && roomState.participantCount() >= roomState.minParticipants();
    }
}
