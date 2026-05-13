package com.nemonicworld.invite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.GoneException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.invite.dto.response.InviteJoinResponse;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.invite.repository.InviteRepository;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 초대코드 입장 서비스의 Redis invite 조회와 릴레이 방 입장 분기를 검증합니다.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class InviteServiceImplTest {

    private static final String INVITE_CODE = "A3K9P2";
    private static final String ROOM_CODE = "AB3K9Q";
    private static final String HOST_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String JOINER_UUID = "660e8400-e29b-41d4-a716-446655440000";

    @Mock
    private InviteRepository inviteRepository;

    @Mock
    private AnonymousUserResolver anonymousUserResolver;

    @Mock
    private RelayRoomRepository relayRoomRepository;

    @Mock
    private RelayInviteMetadataSyncService relayInviteMetadataSyncService;

    private InviteServiceImpl inviteService;

    @BeforeEach
    void setUp() {
        inviteService = new InviteServiceImpl(inviteRepository, anonymousUserResolver,
            List.of(new RelayInviteJoinHandler(relayRoomRepository, relayInviteMetadataSyncService, 10L)));
    }

    /**
     * 대기 중인 릴레이 방 초대코드로 신규 사용자가 입장하면 Redis 방 상태에 참여자를 추가합니다.
     */
    @Test
    void joinByInviteCodeAddsNewRelayParticipant(CapturedOutput output) {
        AppUser joiner = user(JOINER_UUID, "다현");
        InviteMetadata invite = activeInvite();
        RelayRoomState roomState = waitingRoom(hostParticipant());
        ArgumentCaptor<RelayRoomState> updatedRoomCaptor = ArgumentCaptor.forClass(RelayRoomState.class);

        given(anonymousUserResolver.resolve(JOINER_UUID)).willReturn(joiner);
        given(inviteRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(invite));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        InviteJoinResponse response = inviteService.joinByInviteCode(INVITE_CODE, JOINER_UUID);

        assertThat(response.boothType()).isEqualTo("relay");
        assertThat(response.roomId()).isEqualTo(ROOM_CODE);
        assertThat(response.roomName()).isEqualTo("초대받은 릴레이 방");
        assertThat(response.hostNickname()).isEqualTo("방장");
        assertThat(response.currentParticipants()).isEqualTo(2);
        assertThat(response.maxParticipants()).isEqualTo(6);
        assertThat(response.yourRole()).isEqualTo("participant");
        assertThat(response.alreadyJoined()).isFalse();

        verify(relayRoomRepository).saveIfUnchanged(any(RelayRoomState.class), updatedRoomCaptor.capture());
        assertThat(updatedRoomCaptor.getValue().participants()).extracting(RelayRoomParticipant::userUuid)
            .containsExactly(HOST_UUID, JOINER_UUID);
        assertThat(output.getOut()).contains("\"event_name\":\"relay_participant_joined\"")
            .contains("\"room_id\":\"%s\"".formatted(ROOM_CODE)).contains("\"uuid\":\"%s\"".formatted(JOINER_UUID))
            .contains("\"reconnect_attempt\":false").contains("\"already_joined\":false");
    }

    /**
     * 이미 참여 중인 사용자는 중복 추가하지 않고 멱등 응답을 반환합니다.
     */
    @Test
    void joinByInviteCodeReturnsAlreadyJoinedForExistingParticipant(CapturedOutput output) {
        AppUser joiner = user(JOINER_UUID, "다현");
        RelayRoomParticipant existingParticipant = participant(JOINER_UUID, "다현", false, 1);
        RelayRoomState roomState = waitingRoom(hostParticipant(), existingParticipant);

        given(anonymousUserResolver.resolve(JOINER_UUID)).willReturn(joiner);
        given(inviteRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(activeInvite()));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        InviteJoinResponse response = inviteService.joinByInviteCode(INVITE_CODE, JOINER_UUID);

        assertThat(response.currentParticipants()).isEqualTo(2);
        assertThat(response.alreadyJoined()).isTrue();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
        assertThat(output.getOut()).contains("\"event_name\":\"relay_participant_joined\"")
            .contains("\"room_id\":\"%s\"".formatted(ROOM_CODE)).contains("\"uuid\":\"%s\"".formatted(JOINER_UUID))
            .contains("\"reconnect_attempt\":true").contains("\"already_joined\":true");
    }

    /**
     * 형식이 맞지 않는 초대코드는 Redis 조회 전에 400으로 거절합니다.
     */
    @Test
    void joinByInviteCodeRejectsInvalidInviteCode() {
        assertThatThrownBy(() -> inviteService.joinByInviteCode("bad", JOINER_UUID))
            .isInstanceOf(BadRequestException.class).hasMessage("유효하지 않은 초대코드 형식입니다.");
    }

    /**
     * Redis에 없는 초대코드는 404로 변환합니다.
     */
    @Test
    void joinByInviteCodeRejectsMissingInvite() {
        AppUser joiner = user(JOINER_UUID, "다현");

        given(anonymousUserResolver.resolve(JOINER_UUID)).willReturn(joiner);
        given(inviteRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inviteService.joinByInviteCode(INVITE_CODE, JOINER_UUID))
            .isInstanceOf(NotFoundException.class).hasMessage("초대코드를 찾을 수 없습니다.");
    }

    /**
     * 초대 메타데이터가 남아 있어도 expiresAt이 지났으면 410으로 응답합니다.
     */
    @Test
    void joinByInviteCodeRejectsExpiredInvite() {
        AppUser joiner = user(JOINER_UUID, "다현");
        InviteMetadata expiredInvite = new InviteMetadata(INVITE_CODE, "relay", ROOM_CODE, "초대받은 릴레이 방",
            LocalDateTime.now().minusMinutes(1));

        given(anonymousUserResolver.resolve(JOINER_UUID)).willReturn(joiner);
        given(inviteRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(expiredInvite));

        assertThatThrownBy(() -> inviteService.joinByInviteCode(INVITE_CODE, JOINER_UUID))
            .isInstanceOf(GoneException.class).hasMessage("만료된 초대코드입니다.");
    }

    /**
     * 방 저장 구조가 아직 없는 부스 타입은 공통 검증 이후 지원하지 않는 타입으로 거절합니다.
     */
    @Test
    void joinByInviteCodeRejectsUnsupportedBoothType() {
        AppUser joiner = user(JOINER_UUID, "다현");
        InviteMetadata flipbookInvite = new InviteMetadata(INVITE_CODE, "flipbook", ROOM_CODE, "초대받은 플립북 방",
            LocalDateTime.now().plusHours(1));

        given(anonymousUserResolver.resolve(JOINER_UUID)).willReturn(joiner);
        given(inviteRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(flipbookInvite));

        assertThatThrownBy(() -> inviteService.joinByInviteCode(INVITE_CODE, JOINER_UUID))
            .isInstanceOf(BadRequestException.class).hasMessage("지원하지 않는 부스 타입입니다.");
    }

    /**
     * 신규 입장 시 정원이 이미 가득 찬 방이면 409로 응답합니다.
     */
    @Test
    void joinByInviteCodeRejectsFullRoom() {
        AppUser joiner = user(JOINER_UUID, "다현");
        RelayRoomState fullRoom = waitingRoom(hostParticipant(),
            participant("00000000-0000-0000-0000-000000000001", "참여자1", false, 1),
            participant("00000000-0000-0000-0000-000000000002", "참여자2", false, 2),
            participant("00000000-0000-0000-0000-000000000003", "참여자3", false, 3),
            participant("00000000-0000-0000-0000-000000000004", "참여자4", false, 4),
            participant("00000000-0000-0000-0000-000000000005", "참여자5", false, 5));

        given(anonymousUserResolver.resolve(JOINER_UUID)).willReturn(joiner);
        given(inviteRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(activeInvite()));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(fullRoom));

        assertThatThrownBy(() -> inviteService.joinByInviteCode(INVITE_CODE, JOINER_UUID))
            .isInstanceOf(ConflictException.class).hasMessage("정원이 가득 찬 방입니다.");
    }

    /**
     * 이미 게임이 시작된 릴레이 방에는 신규 사용자가 초대코드로 입장할 수 없습니다.
     */
    @Test
    void joinByInviteCodeRejectsNewRelayParticipantWhenGameIsPlaying() {
        AppUser joiner = user(JOINER_UUID, "다현");
        RelayRoomState roomState = room(RelayRoomStatus.PLAYING, hostParticipant());

        given(anonymousUserResolver.resolve(JOINER_UUID)).willReturn(joiner);
        given(inviteRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(activeInvite()));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> inviteService.joinByInviteCode(INVITE_CODE, JOINER_UUID))
            .isInstanceOf(ConflictException.class).hasMessage("게임이 진행 중입니다.");

        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verify(relayInviteMetadataSyncService, never()).syncWithRoomState(any());
    }

    /**
     * 게임 중 끊긴 기존 참여자는 재접속 유예 시간이 지나면 초대코드 복귀도 거부됩니다.
     */
    @Test
    void joinByInviteCodeRejectsExistingRelayParticipantAfterReconnectGracePeriod() {
        AppUser joiner = user(JOINER_UUID, "다현");
        RelayRoomState roomState = room(RelayRoomStatus.PLAYING, hostParticipant(),
            disconnectedParticipant(JOINER_UUID, "다현", false, 1, 11));

        given(anonymousUserResolver.resolve(JOINER_UUID)).willReturn(joiner);
        given(inviteRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(activeInvite()));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> inviteService.joinByInviteCode(INVITE_CODE, JOINER_UUID))
            .isInstanceOf(ConflictException.class).hasMessage("재접속 가능 시간이 만료되어 게임에 다시 참여할 수 없습니다.");

        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verify(relayInviteMetadataSyncService, never()).syncWithRoomState(any());
    }

    /**
     * 이미 이탈 확정된 릴레이 참여자는 초대코드 복귀도 거부됩니다.
     */
    @Test
    void joinByInviteCodeRejectsDroppedRelayParticipant() {
        AppUser joiner = user(JOINER_UUID, "다현");
        RelayRoomState roomState = room(RelayRoomStatus.PLAYING, hostParticipant(),
            droppedParticipant(JOINER_UUID, "다현", false, 1));

        given(anonymousUserResolver.resolve(JOINER_UUID)).willReturn(joiner);
        given(inviteRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(activeInvite()));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> inviteService.joinByInviteCode(INVITE_CODE, JOINER_UUID))
            .isInstanceOf(ConflictException.class).hasMessage("재접속 가능 시간이 만료되어 게임에 다시 참여할 수 없습니다.");

        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verify(relayInviteMetadataSyncService, never()).syncWithRoomState(any());
    }

    /**
     * 릴레이 방에서 강퇴된 UUID는 초대코드 입장 경로로도 재입장할 수 없습니다.
     */
    @Test
    void joinByInviteCodeRejectsKickedRelayUser() {
        AppUser joiner = user(JOINER_UUID, "다현");
        RelayRoomState roomState = waitingRoom(hostParticipant()).withParticipantsAndKickedUserUuids(
            List.of(hostParticipant()), List.of(JOINER_UUID), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        given(anonymousUserResolver.resolve(JOINER_UUID)).willReturn(joiner);
        given(inviteRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(activeInvite()));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> inviteService.joinByInviteCode(INVITE_CODE, JOINER_UUID))
            .isInstanceOf(ForbiddenException.class).hasMessage("강퇴된 방에는 다시 입장할 수 없습니다.");
    }

    private InviteMetadata activeInvite() {
        return new InviteMetadata(INVITE_CODE, "relay", ROOM_CODE, "초대받은 릴레이 방", LocalDateTime.now().plusHours(1));
    }

    private RelayRoomState waitingRoom(RelayRoomParticipant... participants) {
        return room(RelayRoomStatus.WAITING, participants);
    }

    private RelayRoomState room(RelayRoomStatus status, RelayRoomParticipant... participants) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomState(ROOM_CODE, status, HOST_UUID, 45, 2, 6, null, List.of(participants),
            now.minusMinutes(5), now);
    }

    private RelayRoomParticipant hostParticipant() {
        return participant(HOST_UUID, "방장", true, 0);
    }

    private RelayRoomParticipant participant(String userUuid, String nickname, boolean host, int joinOrder) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomParticipant(userUuid, nickname, host, joinOrder, true, null, now);
    }

    private RelayRoomParticipant disconnectedParticipant(String userUuid, String nickname, boolean host, int joinOrder,
        int disconnectedSecondsAgo) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomParticipant(userUuid, nickname, host, joinOrder, false,
            now.minusSeconds(disconnectedSecondsAgo), now.minusMinutes(5));
    }

    private RelayRoomParticipant droppedParticipant(String userUuid, String nickname, boolean host, int joinOrder) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomParticipant(userUuid, nickname, host, joinOrder, false, now.minusSeconds(20),
            now.minusMinutes(5), true, now.minusSeconds(10));
    }

    private AppUser user(String userUuid, String nickname) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        AppUser user = AppUser.createAnonymous(UUID.fromString(userUuid), "test-agent", now);
        user.updateNickname(nickname, now);

        return user;
    }
}
