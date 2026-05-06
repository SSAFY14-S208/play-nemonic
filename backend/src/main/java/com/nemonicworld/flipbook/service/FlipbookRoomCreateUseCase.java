package com.nemonicworld.flipbook.service;

import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCreateResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.invite.repository.InviteRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플립북 방 생성 유스케이스입니다.
 */
@Service
public class FlipbookRoomCreateUseCase {

    private static final String BOOTH_TYPE_FLIPBOOK = "flipbook";
    private static final String DEFAULT_ROOM_NAME_SUFFIX = "의 플립북";

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final InviteRepository inviteRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;

    public FlipbookRoomCreateUseCase(AnonymousUserResolver anonymousUserResolver, RoomCodeGenerator roomCodeGenerator,
        FlipbookRoomRepository flipbookRoomRepository, InviteRepository inviteRepository,
        FlipbookRoomPolicy flipbookRoomPolicy) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.inviteRepository = inviteRepository;
        this.flipbookRoomPolicy = flipbookRoomPolicy;
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
            hostUser.getNickname(), true, FlipbookRoomPolicy.HOST_JOIN_ORDER, true, null, now);
        FlipbookRoomState roomState = new FlipbookRoomState(roomCode, FlipbookRoomStatus.WAITING,
            hostUser.getId().toString(), FlipbookRoomPolicy.DEFAULT_TIME_LIMIT_SECONDS,
            FlipbookRoomPolicy.MIN_PARTICIPANTS, FlipbookRoomPolicy.MAX_PARTICIPANTS, List.of(hostParticipant), now,
            now);

        flipbookRoomRepository.save(roomState);
        inviteRepository.save(createInviteMetadata(roomCode, hostUser, now), FlipbookRoomRepository.ROOM_STATE_TTL);

        return FlipbookRoomCreateResponse.from(roomState);
    }

    /**
     * 플립북 방코드를 공통 초대코드로도 저장해 /invites/{inviteCode}에서 재사용합니다.
     */
    private InviteMetadata createInviteMetadata(String roomCode, AppUser hostUser, LocalDateTime now) {
        return new InviteMetadata(roomCode, BOOTH_TYPE_FLIPBOOK, roomCode,
            hostUser.getNickname() + DEFAULT_ROOM_NAME_SUFFIX, now.plus(FlipbookRoomRepository.ROOM_STATE_TTL));
    }
}
