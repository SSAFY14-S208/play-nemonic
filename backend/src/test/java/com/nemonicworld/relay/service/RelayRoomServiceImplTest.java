package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.files.config.MinioStorageProperties;
import com.nemonicworld.invite.repository.InviteRepository;
import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.relay.dto.request.RelayRoomSubmissionRequest;
import com.nemonicworld.relay.dto.response.RelayRoomKickResponse;
import com.nemonicworld.relay.dto.response.RelayRoomLeaveResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.assignment.RelayRoomAssignmentQueryUseCase;
import com.nemonicworld.relay.service.close.RelayRoomCloseCommand;
import com.nemonicworld.relay.service.close.RelayRoomManualCloseUseCase;
import com.nemonicworld.relay.service.game.RelayRoomPartAdvanceService;
import com.nemonicworld.relay.service.game.RelayRoomStartUseCase;
import com.nemonicworld.relay.service.room.RelayRoomConnectionUseCase;
import com.nemonicworld.relay.service.room.RelayRoomCreateUseCase;
import com.nemonicworld.relay.service.room.RelayRoomJoinUseCase;
import com.nemonicworld.relay.service.room.RelayRoomKickUseCase;
import com.nemonicworld.relay.service.room.RelayRoomLeaveUseCase;
import com.nemonicworld.relay.service.room.RelayRoomQueryUseCase;
import com.nemonicworld.relay.service.room.RelayRoomSettingsUseCase;
import com.nemonicworld.relay.service.submission.RelayRoomSubmissionUseCase;
import com.nemonicworld.relay.service.submission.RelaySubmissionStorage;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.service.support.RelayRoomViewerFactory;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.mock.web.MockMultipartFile;

/**
 * 릴레이 방 서비스의 Redis 낙관적 갱신 재시도 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class RelayRoomServiceImplTest {

    private static final String ROOM_CODE = "AB3K9Q";

    @Mock
    private AnonymousUserResolver anonymousUserResolver;

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    @Mock
    private RelayRoomRepository relayRoomRepository;

    @Mock
    private InviteRepository inviteRepository;

    @Mock
    private RelaySubmissionStorage relaySubmissionStorage;

    private RelayRoomService relayRoomService;

    @BeforeEach
    void setUp() {
        RelayRoomPolicy relayRoomPolicy = new RelayRoomPolicy(roomCodeGenerator, relayRoomRepository);
        RelayRoomViewerFactory relayRoomViewerFactory = new RelayRoomViewerFactory(relayRoomPolicy);
        RelayRoomPartAdvanceService relayRoomPartAdvanceService = new RelayRoomPartAdvanceService();
        relayRoomService = new RelayRoomServiceImpl(
            new RelayRoomCreateUseCase(anonymousUserResolver, roomCodeGenerator, relayRoomRepository, inviteRepository,
                relayRoomPolicy),
            new RelayRoomQueryUseCase(anonymousUserResolver, relayRoomPolicy, relayRoomViewerFactory),
            new RelayRoomJoinUseCase(anonymousUserResolver, relayRoomRepository, relayRoomPolicy,
                relayRoomViewerFactory),
            new RelayRoomKickUseCase(anonymousUserResolver, relayRoomRepository, relayRoomPolicy),
            new RelayRoomLeaveUseCase(anonymousUserResolver, relayRoomRepository, relayRoomPolicy),
            new RelayRoomSettingsUseCase(anonymousUserResolver, relayRoomRepository, relayRoomPolicy,
                relayRoomViewerFactory),
            new RelayRoomStartUseCase(
                anonymousUserResolver, relayRoomRepository, relayRoomPolicy, relayRoomViewerFactory),
            new RelayRoomAssignmentQueryUseCase(anonymousUserResolver, relayRoomPolicy),
            new RelayRoomSubmissionUseCase(anonymousUserResolver, relayRoomRepository, relayRoomPolicy,
                relayRoomPartAdvanceService, relaySubmissionStorage, minioStorageProperties()),
            new RelayRoomManualCloseUseCase(anonymousUserResolver, relayRoomPolicy,
                new RelayRoomCloseCommand(relayRoomRepository)),
            new RelayRoomConnectionUseCase(anonymousUserResolver, relayRoomRepository, relayRoomPolicy,
                relayRoomViewerFactory));
    }

    /**
     * 신규 입장 저장 중 충돌이 나면 최신 방 상태를 다시 읽고 다음 joinOrder로 재시도합니다.
     */
    @Test
    void joinRoomRetriesOptimisticSaveConflictWithLatestRoomState() {
        UUID hostUuid = UUID.randomUUID();
        UUID otherJoinerUuid = UUID.randomUUID();
        UUID joinerUuid = UUID.randomUUID();
        AppUser joiner = appUserWithNickname(joinerUuid, "포도");
        RelayRoomState firstReadRoomState = roomState(participant(hostUuid, "망고", true, 0));
        RelayRoomState secondReadRoomState = roomState(participant(hostUuid, "망고", true, 0),
            participant(otherJoinerUuid, "사과", false, 1));
        given(anonymousUserResolver.resolve(joinerUuid.toString())).willReturn(joiner);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        givenRoomStateReads(firstReadRoomState, secondReadRoomState);
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false, true);

        RelayRoomStateResponse response = relayRoomService.joinRoom(joinerUuid.toString(), ROOM_CODE);

        assertThat(response.participantCount()).isEqualTo(3);
        assertThat(response.participants()).extracting("userUuid").containsExactly(hostUuid.toString(),
            otherJoinerUuid.toString(), joinerUuid.toString());
        assertThat(response.participants().get(2).joinOrder()).isEqualTo(2);

        ArgumentCaptor<RelayRoomState> expectedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository, times(2)).saveIfUnchanged(expectedStateCaptor.capture(),
            updatedStateCaptor.capture());
        assertThat(expectedStateCaptor.getAllValues()).containsExactly(firstReadRoomState, secondReadRoomState);
        assertThat(updatedStateCaptor.getAllValues().get(0).participantCount()).isEqualTo(2);
        assertThat(updatedStateCaptor.getAllValues().get(1).participantCount()).isEqualTo(3);
    }

    /**
     * 짧은 재시도 횟수를 모두 소진하면 충돌 예외로 올려 클라이언트에는 공통 409 응답이 나가게 합니다.
     */
    @Test
    void joinRoomFailsWhenOptimisticSaveConflictsKeepHappening() {
        UUID hostUuid = UUID.randomUUID();
        UUID joinerUuid = UUID.randomUUID();
        AppUser joiner = appUserWithNickname(joinerUuid, "포도");
        RelayRoomState roomState = roomState(participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(joinerUuid.toString())).willReturn(joiner);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> relayRoomService.joinRoom(joinerUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage("릴레이 방 상태를 갱신할 수 없습니다.");

        verify(relayRoomRepository, times(3)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
    }

    /**
     * 강퇴 저장 중 충돌이 나면 최신 방 상태를 다시 읽고 대상 UUID만 제거하며 joinOrder는 재정렬하지 않습니다.
     */
    @Test
    void kickParticipantRetriesOptimisticSaveConflictWithLatestRoomState() {
        UUID hostUuid = UUID.randomUUID();
        UUID otherUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        RelayRoomState firstReadRoomState = roomState(participant(hostUuid, "망고", true, 0),
            participant(targetUuid, "포도", false, 1));
        RelayRoomState secondReadRoomState = roomState(participant(hostUuid, "망고", true, 0),
            participant(otherUuid, "사과", false, 3), participant(targetUuid, "포도", false, 5));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(anonymousUserResolver.parseUuid(targetUuid.toString())).willReturn(targetUuid);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        givenRoomStateReads(firstReadRoomState, secondReadRoomState);
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false, true);

        RelayRoomKickResponse response = relayRoomService.kickParticipant(hostUuid.toString(), ROOM_CODE,
            targetUuid.toString());

        assertThat(response.kickedUserUuid()).isEqualTo(targetUuid.toString());
        assertThat(response.kickedNickname()).isEqualTo("포도");
        assertThat(response.participantCount()).isEqualTo(2);

        ArgumentCaptor<RelayRoomState> expectedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository, times(2)).saveIfUnchanged(expectedStateCaptor.capture(),
            updatedStateCaptor.capture());
        assertThat(expectedStateCaptor.getAllValues()).containsExactly(firstReadRoomState, secondReadRoomState);
        RelayRoomState updatedRoomState = updatedStateCaptor.getAllValues().get(1);
        assertThat(updatedRoomState.participants()).extracting(RelayRoomParticipant::userUuid)
            .containsExactly(hostUuid.toString(), otherUuid.toString());
        assertThat(updatedRoomState.participants()).extracting(RelayRoomParticipant::joinOrder).containsExactly(0, 3);
        assertThat(updatedRoomState.kickedUserUuids()).containsExactly(targetUuid.toString());
    }

    /**
     * 강퇴 저장 재시도 횟수를 모두 소진하면 내부 오류로 전파합니다.
     */
    @Test
    void kickParticipantFailsWhenOptimisticSaveConflictsKeepHappening() {
        UUID hostUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        RelayRoomState roomState = roomState(participant(hostUuid, "망고", true, 0),
            participant(targetUuid, "포도", false, 1));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(anonymousUserResolver.parseUuid(targetUuid.toString())).willReturn(targetUuid);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(
            () -> relayRoomService.kickParticipant(hostUuid.toString(), ROOM_CODE, targetUuid.toString()))
            .isInstanceOf(ConflictException.class).hasMessage("릴레이 방 상태를 갱신할 수 없습니다.");

        verify(relayRoomRepository, times(3)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
    }

    /**
     * 강퇴된 UUID는 같은 roomCode에 다시 입장할 수 없습니다.
     */
    @Test
    void joinRoomRejectsKickedUser() {
        UUID hostUuid = UUID.randomUUID();
        UUID kickedUuid = UUID.randomUUID();
        AppUser kickedUser = appUserWithNickname(kickedUuid, "포도");
        RelayRoomState roomState = roomState(participant(hostUuid, "망고", true, 0)).withParticipantsAndKickedUserUuids(
            List.of(participant(hostUuid, "망고", true, 0)), List.of(kickedUuid.toString()),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        given(anonymousUserResolver.resolve(kickedUuid.toString())).willReturn(kickedUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> relayRoomService.joinRoom(kickedUuid.toString(), ROOM_CODE))
            .isInstanceOf(ForbiddenException.class).hasMessage("강퇴된 방에는 다시 입장할 수 없습니다.");
    }

    /**
     * 방장 퇴장 저장 중 충돌이 나면 최신 방 상태를 다시 읽고 joinOrder가 가장 작은 남은 참여자에게 방장을 승계합니다.
     */
    @Test
    void leaveRoomRetriesOptimisticSaveConflictWithLatestRoomStateAndTransfersHost() {
        UUID hostUuid = UUID.randomUUID();
        UUID firstCandidateUuid = UUID.randomUUID();
        UUID secondCandidateUuid = UUID.randomUUID();
        UUID latestCandidateUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        RelayRoomState firstReadRoomState = roomState(participant(hostUuid, "망고", true, 0),
            participant(firstCandidateUuid, "포도", false, 5));
        RelayRoomState secondReadRoomState = roomState(participant(hostUuid, "망고", true, 0),
            participant(secondCandidateUuid, "사과", false, 3), participant(latestCandidateUuid, "배", false, 1));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        givenRoomStateReads(firstReadRoomState, secondReadRoomState);
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false, true);

        RelayRoomLeaveResponse response = relayRoomService.leaveRoom(hostUuid.toString(), ROOM_CODE);

        assertThat(response.leftUserUuid()).isEqualTo(hostUuid.toString());
        assertThat(response.hostChanged()).isTrue();
        assertThat(response.newHostUserUuid()).isEqualTo(latestCandidateUuid.toString());
        assertThat(response.newHostNickname()).isEqualTo("배");
        assertThat(response.participantCount()).isEqualTo(2);
        assertThat(response.roomStatus()).isEqualTo(RelayRoomStatus.WAITING);

        ArgumentCaptor<RelayRoomState> expectedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository, times(2)).saveIfUnchanged(expectedStateCaptor.capture(),
            updatedStateCaptor.capture());
        assertThat(expectedStateCaptor.getAllValues()).containsExactly(firstReadRoomState, secondReadRoomState);
        RelayRoomState updatedRoomState = updatedStateCaptor.getAllValues().get(1);
        assertThat(updatedRoomState.hostUserUuid()).isEqualTo(latestCandidateUuid.toString());
        assertThat(updatedRoomState.participants()).extracting(RelayRoomParticipant::userUuid)
            .containsExactly(secondCandidateUuid.toString(), latestCandidateUuid.toString());
        assertThat(updatedRoomState.participants()).extracting(RelayRoomParticipant::joinOrder).containsExactly(3, 1);
        assertThat(updatedRoomState.participants()).extracting(RelayRoomParticipant::host).containsExactly(false, true);
        assertThat(updatedRoomState.kickedUserUuids()).isEqualTo(secondReadRoomState.kickedUserUuids());
    }

    /**
     * 퇴장 저장 재시도 횟수를 모두 소진하면 내부 오류로 전파합니다.
     */
    @Test
    void leaveRoomFailsWhenOptimisticSaveConflictsKeepHappening() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        AppUser participantUser = appUserWithNickname(participantUuid, "포도");
        RelayRoomState roomState = roomState(participant(hostUuid, "망고", true, 0),
            participant(participantUuid, "포도", false, 1));
        given(anonymousUserResolver.resolve(participantUuid.toString())).willReturn(participantUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> relayRoomService.leaveRoom(participantUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage("릴레이 방 상태를 갱신할 수 없습니다.");

        verify(relayRoomRepository, times(3)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
    }

    /**
     * 설정 변경 저장 중 충돌이 나면 최신 방 상태를 다시 읽고 요청한 제한 시간을 재적용합니다.
     */
    @Test
    void updateRoomSettingsRetriesOptimisticSaveConflictWithLatestRoomState() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        RelayRoomState firstReadRoomState = roomState(participant(hostUuid, "망고", true, 0));
        RelayRoomState secondReadRoomState = new RelayRoomState(ROOM_CODE, RelayRoomStatus.WAITING,
            firstReadRoomState.hostUserUuid(), 45, firstReadRoomState.minParticipants(),
            firstReadRoomState.maxParticipants(), firstReadRoomState.currentPart(), firstReadRoomState.participants(),
            firstReadRoomState.createdAt(), firstReadRoomState.updatedAt().plusSeconds(1));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        givenRoomStateReads(firstReadRoomState, secondReadRoomState);
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false, true);

        RelayRoomStateResponse response = relayRoomService.updateRoomSettings(hostUuid.toString(), ROOM_CODE,
            new RelayRoomSettingsRequest(30));

        assertThat(response.timeLimitSeconds()).isEqualTo(30);
        assertThat(response.viewer().participant()).isTrue();
        assertThat(response.viewer().host()).isTrue();

        ArgumentCaptor<RelayRoomState> expectedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository, times(2)).saveIfUnchanged(expectedStateCaptor.capture(),
            updatedStateCaptor.capture());
        assertThat(expectedStateCaptor.getAllValues()).containsExactly(firstReadRoomState, secondReadRoomState);
        assertThat(updatedStateCaptor.getAllValues()).extracting(RelayRoomState::timeLimitSeconds).containsExactly(30,
            30);
        assertThat(updatedStateCaptor.getAllValues().get(1).participants())
            .isEqualTo(secondReadRoomState.participants());
        assertThat(updatedStateCaptor.getAllValues().get(1).createdAt()).isEqualTo(secondReadRoomState.createdAt());
    }

    /**
     * 설정 변경 재시도 횟수를 모두 소진하면 충돌 예외로 올려 클라이언트에는 공통 409 응답이 나가게 합니다.
     */
    @Test
    void updateRoomSettingsFailsWhenOptimisticSaveConflictsKeepHappening() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        RelayRoomState roomState = roomState(participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(
            () -> relayRoomService.updateRoomSettings(hostUuid.toString(), ROOM_CODE, new RelayRoomSettingsRequest(45)))
            .isInstanceOf(ConflictException.class).hasMessage("릴레이 방 상태를 갱신할 수 없습니다.");

        verify(relayRoomRepository, times(3)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
    }

    /**
     * WebSocket 연결 성공 시 기존 participant만 connected=true, disconnectedAt=null로 갱신합니다.
     */
    @Test
    void connectRoomMarksExistingParticipantConnected() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        RelayRoomParticipant disconnectedParticipant = participant(hostUuid, "망고", true, 0, false,
            LocalDateTime.now().minusSeconds(3).truncatedTo(ChronoUnit.SECONDS));
        RelayRoomState roomState = roomState(disconnectedParticipant);
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomStateResponse response = relayRoomService.connectRoom(hostUuid.toString(), ROOM_CODE);

        assertThat(response.participants().get(0).connected()).isTrue();

        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository).saveIfUnchanged(any(RelayRoomState.class), updatedStateCaptor.capture());
        RelayRoomParticipant storedParticipant = updatedStateCaptor.getValue().participants().get(0);
        assertThat(storedParticipant.connected()).isTrue();
        assertThat(storedParticipant.disconnectedAt()).isNull();
        assertThat(storedParticipant.nickname()).isEqualTo("망고");
        assertThat(storedParticipant.host()).isTrue();
        assertThat(storedParticipant.joinOrder()).isZero();
    }

    /**
     * WebSocket 연결 해제 시 기존 participant를 connected=false와 현재 disconnectedAt으로 갱신합니다.
     */
    @Test
    void disconnectRoomMarksCurrentParticipantDisconnected() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = roomState(participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.parseUuid(hostUuid.toString())).willReturn(hostUuid);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomStateResponse response = relayRoomService.disconnectRoom(hostUuid.toString(), ROOM_CODE);

        assertThat(response.participants().get(0).connected()).isFalse();

        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository).saveIfUnchanged(any(RelayRoomState.class), updatedStateCaptor.capture());
        RelayRoomParticipant storedParticipant = updatedStateCaptor.getValue().participants().get(0);
        assertThat(storedParticipant.connected()).isFalse();
        assertThat(storedParticipant.disconnectedAt()).isNotNull();
        assertThat(storedParticipant.nickname()).isEqualTo("망고");
        assertThat(storedParticipant.host()).isTrue();
        assertThat(storedParticipant.joinOrder()).isZero();
    }

    /**
     * REST 입장 API로 등록되지 않은 사용자의 WebSocket 연결은 participant를 새로 만들지 않고 거부합니다.
     */
    @Test
    void connectRoomRejectsUserWhoIsNotRoomParticipant() {
        UUID hostUuid = UUID.randomUUID();
        UUID viewerUuid = UUID.randomUUID();
        AppUser viewerUser = appUserWithNickname(viewerUuid, "포도");
        RelayRoomState roomState = roomState(participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(viewerUuid.toString())).willReturn(viewerUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> relayRoomService.connectRoom(viewerUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage("릴레이 방에 참여하지 않은 사용자입니다.");
    }

    /**
     * 강퇴된 UUID의 WebSocket 재연결은 participant 연결 갱신 전에 거부합니다.
     */
    @Test
    void connectRoomRejectsKickedUser() {
        UUID hostUuid = UUID.randomUUID();
        UUID kickedUuid = UUID.randomUUID();
        AppUser kickedUser = appUserWithNickname(kickedUuid, "포도");
        RelayRoomParticipant kickedParticipant = participant(kickedUuid, "포도", false, 1);
        RelayRoomState baseRoomState = roomState(participant(hostUuid, "망고", true, 0), kickedParticipant);
        RelayRoomState roomState = baseRoomState.withParticipantsAndKickedUserUuids(baseRoomState.participants(),
            List.of(kickedUuid.toString()), baseRoomState.updatedAt());
        given(anonymousUserResolver.resolve(kickedUuid.toString())).willReturn(kickedUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> relayRoomService.connectRoom(kickedUuid.toString(), ROOM_CODE))
            .isInstanceOf(ForbiddenException.class).hasMessage("강퇴된 방에는 다시 입장할 수 없습니다.");
    }

    /**
     * 종료된 방에는 WebSocket 연결 상태 갱신을 허용하지 않습니다.
     */
    @Test
    void connectRoomRejectsFinishedRoom() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        RelayRoomState roomState = roomState(RelayRoomStatus.FINISHED, participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> relayRoomService.connectRoom(hostUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage("이미 종료된 방입니다.");
    }

    /**
     * 게임 시작 저장 중 충돌이 나면 최신 방 상태를 다시 읽고 그 시점의 참여자 순서로 배정표를 생성합니다.
     */
    @Test
    void startRoomRetriesOptimisticSaveConflictWithLatestRoomState() {
        UUID hostUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        UUID thirdUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "Mango");
        RelayRoomState firstReadRoomState = roomState(participant(hostUuid, "Mango", true, 0),
            participant(secondUuid, "Peach", false, 1));
        RelayRoomState secondReadRoomState = roomState(participant(hostUuid, "Mango", true, 0),
            participant(secondUuid, "Peach", false, 1), participant(thirdUuid, "Berry", false, 2));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        givenRoomStateReads(firstReadRoomState, secondReadRoomState);
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false, true);

        RelayRoomStateResponse response = relayRoomService.startRoom(hostUuid.toString(), ROOM_CODE);

        assertThat(response.status()).isEqualTo(RelayRoomStatus.PLAYING);
        assertThat(response.currentPart()).isEqualTo(RelayDrawingPart.FACE);
        assertThat(response.assignmentCount()).isEqualTo(9);

        ArgumentCaptor<RelayRoomState> expectedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository, times(2)).saveIfUnchanged(expectedStateCaptor.capture(),
            updatedStateCaptor.capture());
        assertThat(expectedStateCaptor.getAllValues()).containsExactly(firstReadRoomState, secondReadRoomState);

        RelayRoomState startedRoomState = updatedStateCaptor.getAllValues().get(1);
        assertThat(startedRoomState.status()).isEqualTo(RelayRoomStatus.PLAYING);
        assertThat(startedRoomState.currentPart()).isEqualTo(RelayDrawingPart.FACE);
        assertThat(Duration.between(startedRoomState.partStartedAt(), startedRoomState.partDeadlineAt()))
            .isEqualTo(Duration.ofSeconds(startedRoomState.timeLimitSeconds()));
        assertThat(startedRoomState.participants()).isEqualTo(secondReadRoomState.participants());
        assertThat(startedRoomState.createdAt()).isEqualTo(secondReadRoomState.createdAt());
        assertThat(startedRoomState.assignments()).hasSize(9);
        assertAssignment(startedRoomState.assignments().get(0), 0, RelayDrawingPart.FACE, hostUuid);
        assertAssignment(startedRoomState.assignments().get(1), 0, RelayDrawingPart.BODY, secondUuid);
        assertAssignment(startedRoomState.assignments().get(2), 0, RelayDrawingPart.LEGS, thirdUuid);
        assertAssignment(startedRoomState.assignments().get(3), 1, RelayDrawingPart.FACE, secondUuid);
        assertAssignment(startedRoomState.assignments().get(4), 1, RelayDrawingPart.BODY, thirdUuid);
        assertAssignment(startedRoomState.assignments().get(5), 1, RelayDrawingPart.LEGS, hostUuid);
        assertThat(startedRoomState.assignments()).extracting(RelayRoomAssignment::status)
            .containsOnly(RelayAssignmentStatus.PENDING);
    }

    /**
     * 게임 시작 저장 재시도 횟수를 모두 소진하면 내부 오류로 전파합니다.
     */
    @Test
    void startRoomFailsWhenOptimisticSaveConflictsKeepHappening() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "Mango");
        RelayRoomState roomState = roomState(participant(hostUuid, "Mango", true, 0),
            participant(participantUuid, "Peach", false, 1));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> relayRoomService.startRoom(hostUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage("릴레이 방 상태를 갱신할 수 없습니다.");

        verify(relayRoomRepository, times(3)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
    }

    /**
     * 제출 저장 중 충돌이 나면 최신 방 상태를 다시 읽고 같은 배정에 대해 다시 저장을 시도합니다.
     */
    @Test
    void submitCurrentPartRetriesOptimisticSaveConflictWithLatestRoomState() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "Mango");
        RelayRoomState firstReadRoomState = playingRoomState(RelayDrawingPart.FACE,
            List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        RelayRoomState secondReadRoomState = playingRoomState(RelayDrawingPart.FACE,
            List.of(assignment(0, RelayDrawingPart.FACE, hostUuid), assignment(1, RelayDrawingPart.FACE, hostUuid)),
            participant(hostUuid, "Mango", true, 0));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        givenRoomStateReads(firstReadRoomState, secondReadRoomState);
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false, true);

        RelayRoomSubmissionResponse response = relayRoomService.submitCurrentPart(hostUuid.toString(), ROOM_CODE,
            submissionRequest(0, RelayDrawingPart.FACE));

        assertThat(response.assignmentStatus()).isEqualTo(RelayAssignmentStatus.SUBMITTED);
        assertThat(response.submittedCount()).isEqualTo(1);
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.currentPartCompleted()).isFalse();
        verify(relaySubmissionStorage, times(4)).upload(any(String.class), any());

        ArgumentCaptor<RelayRoomState> expectedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository, times(2)).saveIfUnchanged(expectedStateCaptor.capture(),
            updatedStateCaptor.capture());
        assertThat(expectedStateCaptor.getAllValues()).containsExactly(firstReadRoomState, secondReadRoomState);
        assertThat(updatedStateCaptor.getAllValues().get(1).assignments().get(0).status())
            .isEqualTo(RelayAssignmentStatus.SUBMITTED);
        assertThat(updatedStateCaptor.getAllValues().get(1).assignments().get(0).objectKey())
            .isEqualTo("relay/tmp/%s/0/face.png".formatted(ROOM_CODE));
    }

    /**
     * 제출 저장 충돌이 재시도 한도를 넘으면 내부 오류로 전파합니다.
     */
    @Test
    void submitCurrentPartFailsWhenOptimisticSaveConflictsKeepHappening() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "Mango");
        RelayRoomState roomState = playingRoomState(RelayDrawingPart.FACE,
            List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> relayRoomService.submitCurrentPart(hostUuid.toString(), ROOM_CODE,
            submissionRequest(0, RelayDrawingPart.FACE))).isInstanceOf(ConflictException.class);

        verify(relayRoomRepository, times(3)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
        verify(relaySubmissionStorage, times(6)).upload(any(String.class), any());
    }

    @Test
    void submitCurrentPartAdvancesToBodyWhenFaceAssignmentsAreCompleted() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "Mango");
        RelayRoomState roomState = playingRoomState(RelayDrawingPart.FACE,
            List.of(assignment(0, RelayDrawingPart.FACE, hostUuid),
                assignment(1, RelayDrawingPart.FACE, participantUuid, RelayAssignmentStatus.SUBMITTED,
                    "relay/tmp/AB3K9Q/1/face.png", "relay/tmp/AB3K9Q/1/face-hint.png", false, false)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomSubmissionResponse response = relayRoomService.submitCurrentPart(hostUuid.toString(), ROOM_CODE,
            submissionRequest(0, RelayDrawingPart.FACE));

        assertThat(response.currentPartCompleted()).isTrue();
        assertThat(response.advanced()).isTrue();
        assertThat(response.nextPart()).isEqualTo(RelayDrawingPart.BODY);
        assertThat(response.allPartsCompleted()).isFalse();
        assertThat(response.roomStatus()).isEqualTo(RelayRoomStatus.PLAYING);

        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository).saveIfUnchanged(any(RelayRoomState.class), updatedStateCaptor.capture());
        RelayRoomState updatedRoomState = updatedStateCaptor.getValue();
        assertThat(updatedRoomState.status()).isEqualTo(RelayRoomStatus.PLAYING);
        assertThat(updatedRoomState.currentPart()).isEqualTo(RelayDrawingPart.BODY);
        assertThat(Duration.between(updatedRoomState.partStartedAt(), updatedRoomState.partDeadlineAt()))
            .isEqualTo(Duration.ofSeconds(updatedRoomState.timeLimitSeconds()));
        assertThat(updatedRoomState.assignments()).extracting(RelayRoomAssignment::canvasIndex).containsExactly(0, 1);
        assertThat(updatedRoomState.assignments()).extracting(RelayRoomAssignment::part)
            .containsExactly(RelayDrawingPart.FACE, RelayDrawingPart.FACE);
    }

    @Test
    void submitCurrentPartMovesToFinalizingWhenLegsAssignmentsAreCompleted() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "Mango");
        RelayRoomState roomState = playingRoomState(RelayDrawingPart.LEGS,
            List.of(assignment(0, RelayDrawingPart.LEGS, hostUuid),
                assignment(1, RelayDrawingPart.LEGS, participantUuid, RelayAssignmentStatus.AUTO_SUBMITTED,
                    "relay/tmp/AB3K9Q/1/legs.png", null, true, true)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomSubmissionResponse response = relayRoomService.submitCurrentPart(hostUuid.toString(), ROOM_CODE,
            submissionRequest(0, RelayDrawingPart.LEGS));

        assertThat(response.currentPartCompleted()).isTrue();
        assertThat(response.advanced()).isTrue();
        assertThat(response.nextPart()).isNull();
        assertThat(response.allPartsCompleted()).isTrue();
        assertThat(response.roomStatus()).isEqualTo(RelayRoomStatus.FINALIZING);

        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository).saveIfUnchanged(any(RelayRoomState.class), updatedStateCaptor.capture());
        RelayRoomState updatedRoomState = updatedStateCaptor.getValue();
        assertThat(updatedRoomState.status()).isEqualTo(RelayRoomStatus.FINALIZING);
        assertThat(updatedRoomState.currentPart()).isEqualTo(RelayDrawingPart.LEGS);
    }

    private void assertAssignment(RelayRoomAssignment assignment, int canvasIndex, RelayDrawingPart part,
        UUID assignedUserUuid) {
        assertThat(assignment.canvasIndex()).isEqualTo(canvasIndex);
        assertThat(assignment.part()).isEqualTo(part);
        assertThat(assignment.assignedUserUuid()).isEqualTo(assignedUserUuid.toString());
        assertThat(assignment.fileId()).isNull();
        assertThat(assignment.objectKey()).isNull();
        assertThat(assignment.hintObjectKey()).isNull();
        assertThat(assignment.submittedAt()).isNull();
        assertThat(assignment.empty()).isFalse();
        assertThat(assignment.autoSubmitted()).isFalse();
    }

    private void givenRoomStateReads(RelayRoomState firstReadRoomState, RelayRoomState secondReadRoomState) {
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(firstReadRoomState))
            .willReturn(Optional.of(secondReadRoomState));
    }

    private AppUser appUserWithNickname(UUID userUuid, String nickname) {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt);
        appUser.updateNickname(nickname, createdAt.plusHours(1));

        return appUser;
    }

    private RelayRoomState roomState(RelayRoomParticipant... participants) {
        return roomState(RelayRoomStatus.WAITING, participants);
    }

    private RelayRoomState roomState(RelayRoomStatus status, RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomState(ROOM_CODE, status, participants[0].userUuid(), 60, 2, 6, null, List.of(participants),
            createdAt, createdAt.plusSeconds(1));
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return participant(userUuid, nickname, host, joinOrder, true, null);
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder,
        boolean connected, LocalDateTime disconnectedAt) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, connected, disconnectedAt,
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private RelayRoomState playingRoomState(RelayDrawingPart currentPart, List<RelayRoomAssignment> assignments,
        RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime startedAt = LocalDateTime.now().minusSeconds(5).truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomState(ROOM_CODE, RelayRoomStatus.PLAYING, participants[0].userUuid(), 45, 2, 6, currentPart,
            List.of(participants), assignments, startedAt, startedAt.plusSeconds(45), startedAt, createdAt,
            createdAt.plusSeconds(1));
    }

    private RelayRoomAssignment assignment(int canvasIndex, RelayDrawingPart part, UUID assignedUserUuid) {
        return new RelayRoomAssignment(canvasIndex, part, assignedUserUuid.toString(), RelayAssignmentStatus.PENDING,
            null, null, null, false, false, null);
    }

    private RelayRoomAssignment assignment(int canvasIndex, RelayDrawingPart part, UUID assignedUserUuid,
        RelayAssignmentStatus status, String objectKey, String hintObjectKey, boolean empty, boolean autoSubmitted) {
        LocalDateTime submittedAt = status == RelayAssignmentStatus.PENDING
            ? null
            : LocalDateTime.now().minusSeconds(5).truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomAssignment(canvasIndex, part, assignedUserUuid.toString(), status, null, objectKey,
            hintObjectKey, empty, autoSubmitted, submittedAt);
    }

    private RelayRoomSubmissionRequest submissionRequest(int canvasIndex, RelayDrawingPart part) {
        return new RelayRoomSubmissionRequest(canvasIndex, part.name(), pngFile("drawingImage", "drawing.png"),
            pngFile("hintImage", "hint.png"));
    }

    private MockMultipartFile pngFile(String name, String originalFileName) {
        return new MockMultipartFile(name, originalFileName, "image/png", "image".getBytes(StandardCharsets.UTF_8));
    }

    private MinioStorageProperties minioStorageProperties() {
        return new MinioStorageProperties("http://localhost:9000", "http://localhost:9000", "minioadmin", "minioadmin",
            "nemonic-local", 10, 10 * 1024 * 1024);
    }
}
