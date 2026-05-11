package com.nemonicworld.flipbook.service;

import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCreateResponse;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.invite.repository.InviteRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

/**
 * 플립북 방 생성 유스케이스입니다.
 */
@Service
public class FlipbookRoomCreateUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final InviteRepository inviteRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    public FlipbookRoomCreateUseCase(AnonymousUserResolver anonymousUserResolver, RoomCodeGenerator roomCodeGenerator,
        FlipbookRoomRepository flipbookRoomRepository, InviteRepository inviteRepository,
        FlipbookRoomPolicy flipbookRoomPolicy, FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.inviteRepository = inviteRepository;
        this.flipbookRoomPolicy = flipbookRoomPolicy;
        this.flipbookInviteMetadataSyncService = flipbookInviteMetadataSyncService;
    }

    /**
     * 기존 익명 사용자를 방장 겸 첫 참여자로 등록하고 플립북 대기방을 생성합니다.
     */
    @Transactional(readOnly = true)
    public FlipbookRoomCreateResponse createRoom(String userUuidValue) {
        AppUser hostUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateNicknameRegistered(hostUser);

        String roomCode = roomCodeGenerator.generateUnique(inviteRepository::existsByInviteCode);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomParticipant hostParticipant = new FlipbookRoomParticipant(hostUser.getId().toString(),
            hostUser.getNickname(), true, FlipbookRoomPolicy.HOST_JOIN_ORDER, false, null, now);
        FlipbookRoomState roomState = new FlipbookRoomState(roomCode, FlipbookRoomStatus.WAITING,
            hostUser.getId().toString(), FlipbookRoomPolicy.DEFAULT_TIME_LIMIT_SECONDS,
            FlipbookRoomPolicy.MIN_PARTICIPANTS, FlipbookRoomPolicy.MAX_PARTICIPANTS, List.of(hostParticipant), now,
            now);

        flipbookRoomRepository.save(roomState);
        flipbookInviteMetadataSyncService.syncWithRoomState(roomState);
        FlipbookRoomEventLogger.apiBusiness("flipbook_room_created",
            metadata("room_id", roomCode, "host_uuid", hostUser.getId(), "min_participants",
                roomState.minParticipants(), "max_participants", roomState.maxParticipants(), "time_limit_seconds",
                roomState.timeLimitSeconds()));

        return FlipbookRoomCreateResponse.from(roomState);
    }
}
