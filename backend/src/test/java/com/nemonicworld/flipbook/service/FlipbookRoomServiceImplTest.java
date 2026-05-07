package com.nemonicworld.flipbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomKickResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomLeaveResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
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

/**
 * 플립북 방 서비스의 Redis 낙관적 갱신 재시도 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class FlipbookRoomServiceImplTest {

    private static final String ROOM_CODE = "FB3K9Q";

    @Mock
    private AnonymousUserResolver anonymousUserResolver;

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    @Mock
    private FlipbookRoomRepository flipbookRoomRepository;

    @Mock
    private FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    private FlipbookRoomSettingsUseCase flipbookRoomSettingsUseCase;
    private FlipbookRoomStartUseCase flipbookRoomStartUseCase;
    private FlipbookRoomKickUseCase flipbookRoomKickUseCase;
    private FlipbookRoomLeaveUseCase flipbookRoomLeaveUseCase;

    @BeforeEach
    void setUp() {
        FlipbookRoomPolicy flipbookRoomPolicy = new FlipbookRoomPolicy(roomCodeGenerator, flipbookRoomRepository);
        FlipbookRoomViewerFactory flipbookRoomViewerFactory = new FlipbookRoomViewerFactory(flipbookRoomPolicy);
        flipbookRoomSettingsUseCase = new FlipbookRoomSettingsUseCase(anonymousUserResolver, flipbookRoomRepository,
            flipbookRoomPolicy, flipbookRoomViewerFactory, flipbookInviteMetadataSyncService);
        flipbookRoomStartUseCase = new FlipbookRoomStartUseCase(anonymousUserResolver, flipbookRoomRepository,
            flipbookRoomPolicy, flipbookRoomViewerFactory, flipbookInviteMetadataSyncService);
        flipbookRoomKickUseCase = new FlipbookRoomKickUseCase(anonymousUserResolver, flipbookRoomRepository,
            flipbookRoomPolicy, flipbookInviteMetadataSyncService);
        flipbookRoomLeaveUseCase = new FlipbookRoomLeaveUseCase(anonymousUserResolver, flipbookRoomRepository,
            flipbookRoomPolicy, flipbookInviteMetadataSyncService);
    }

    /**
     * 방장은 대기 중인 플립북 방의 제한 시간을 허용값 중 하나로 변경할 수 있습니다.
     */
    @Test
    void updateRoomSettingsChangesTimeLimitForHost() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomStateResponse response = flipbookRoomSettingsUseCase.updateRoomSettings(hostUuid.toString(),
            ROOM_CODE, new FlipbookRoomSettingsRequest(30));

        assertThat(response.timeLimitSeconds()).isEqualTo(30);
        assertThat(response.viewer().participant()).isTrue();
        assertThat(response.viewer().host()).isTrue();
        assertThat(response.viewer().canStart()).isFalse();

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());
        FlipbookRoomState updatedRoomState = updatedStateCaptor.getValue();
        assertThat(updatedRoomState.timeLimitSeconds()).isEqualTo(30);
        assertThat(updatedRoomState.participants()).isEqualTo(roomState.participants());
        assertThat(updatedRoomState.createdAt()).isEqualTo(roomState.createdAt());
        assertThat(updatedRoomState.updatedAt()).isAfterOrEqualTo(roomState.updatedAt());
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomState);
    }

    /**
     * 제한 시간은 플립북 정책이 허용한 값만 받을 수 있습니다.
     */
    @Test
    void updateRoomSettingsRejectsInvalidTimeLimit() {
        UUID hostUuid = UUID.randomUUID();

        assertThatThrownBy(() -> flipbookRoomSettingsUseCase.updateRoomSettings(hostUuid.toString(), ROOM_CODE,
            new FlipbookRoomSettingsRequest(10))).isInstanceOf(BadRequestException.class)
            .hasMessage("제한 시간은 30초, 45초, 60초 중 하나여야 합니다.");

        verify(anonymousUserResolver, never()).resolve(anyString());
        verify(flipbookRoomRepository, never()).findByRoomCode(any());
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 참여자는 설정을 조회할 수는 있지만 변경할 수 없습니다.
     */
    @Test
    void updateRoomSettingsRejectsParticipantWhoIsNotHost() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        AppUser participantUser = appUserWithNickname(participantUuid, "다현");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0),
            participant(participantUuid, "다현", false, 1));
        given(anonymousUserResolver.resolve(participantUuid.toString())).willReturn(participantUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomSettingsUseCase.updateRoomSettings(participantUuid.toString(), ROOM_CODE,
            new FlipbookRoomSettingsRequest(60))).isInstanceOf(ForbiddenException.class).hasMessage("방장만 사용할 수 있습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 방에 참여하지 않은 사용자는 설정을 변경할 수 없습니다.
     */
    @Test
    void updateRoomSettingsRejectsNonParticipant() {
        UUID hostUuid = UUID.randomUUID();
        UUID viewerUuid = UUID.randomUUID();
        AppUser viewerUser = appUserWithNickname(viewerUuid, "다현");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(viewerUuid.toString())).willReturn(viewerUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomSettingsUseCase.updateRoomSettings(viewerUuid.toString(), ROOM_CODE,
            new FlipbookRoomSettingsRequest(60))).isInstanceOf(ForbiddenException.class)
            .hasMessage("플립북 방에 참여하지 않은 사용자입니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 게임이 시작된 뒤에는 대기실 전용 설정을 변경할 수 없습니다.
     */
    @Test
    void updateRoomSettingsRejectsNonWaitingRoom() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.PLAYING, 45, participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomSettingsUseCase.updateRoomSettings(hostUuid.toString(), ROOM_CODE,
            new FlipbookRoomSettingsRequest(60))).isInstanceOf(ConflictException.class)
            .hasMessage("대기 중인 방에서만 설정을 변경할 수 있습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 설정 저장 중 충돌이 나면 최신 방 상태를 다시 읽고 요청한 제한 시간을 재적용합니다.
     */
    @Test
    void updateRoomSettingsRetriesOptimisticSaveConflictWithLatestRoomState() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState firstReadRoomState = roomState(FlipbookRoomStatus.WAITING, 45,
            participant(hostUuid, "망고", true, 0));
        FlipbookRoomState secondReadRoomState = roomState(FlipbookRoomStatus.WAITING, 45,
            participant(hostUuid, "망고", true, 0), participant(participantUuid, "다현", false, 1));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(firstReadRoomState),
            Optional.of(secondReadRoomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false, true);

        FlipbookRoomStateResponse response = flipbookRoomSettingsUseCase.updateRoomSettings(hostUuid.toString(),
            ROOM_CODE, new FlipbookRoomSettingsRequest(60));

        assertThat(response.timeLimitSeconds()).isEqualTo(60);
        assertThat(response.participantCount()).isEqualTo(2);

        ArgumentCaptor<FlipbookRoomState> expectedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository, times(2)).saveIfUnchanged(expectedStateCaptor.capture(),
            updatedStateCaptor.capture());
        assertThat(expectedStateCaptor.getAllValues()).containsExactly(firstReadRoomState, secondReadRoomState);
        assertThat(updatedStateCaptor.getAllValues()).extracting(FlipbookRoomState::timeLimitSeconds)
            .containsExactly(60, 60);
        assertThat(updatedStateCaptor.getAllValues().get(1).participants())
            .isEqualTo(secondReadRoomState.participants());
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedStateCaptor.getAllValues().get(1));
    }

    /**
     * 설정 변경 재시도 횟수를 모두 소진하면 클라이언트가 재시도할 수 있는 409 예외를 던집니다.
     */
    @Test
    void updateRoomSettingsFailsWhenOptimisticSaveConflictsKeepHappening() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> flipbookRoomSettingsUseCase.updateRoomSettings(hostUuid.toString(), ROOM_CODE,
            new FlipbookRoomSettingsRequest(45))).isInstanceOf(ConflictException.class)
            .hasMessage("동시 설정 변경 요청이 많아 방 설정을 갱신하지 못했습니다. 다시 시도해주세요.");

        verify(flipbookRoomRepository, times(3)).saveIfUnchanged(any(FlipbookRoomState.class),
            any(FlipbookRoomState.class));
    }

    /**
     * 방장은 참여자가 모두 연결된 대기 중 플립북 방을 시작할 수 있습니다.
     */
    @Test
    void startRoomChangesWaitingRoomToPlaying() {
        UUID hostUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        UUID thirdUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0),
            participant(secondUuid, "다현", false, 1), participant(thirdUuid, "포도", false, 2));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomStateResponse response = flipbookRoomStartUseCase.startRoom(hostUuid.toString(), ROOM_CODE);

        assertThat(response.status()).isEqualTo(FlipbookRoomStatus.PLAYING);
        assertThat(response.currentRound()).isEqualTo(1);
        assertThat(response.totalRounds()).isEqualTo(3);
        assertThat(response.viewer().host()).isTrue();
        assertThat(response.viewer().canStart()).isFalse();

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());
        FlipbookRoomState updatedRoomState = updatedStateCaptor.getValue();
        assertThat(updatedRoomState.status()).isEqualTo(FlipbookRoomStatus.PLAYING);
        assertThat(updatedRoomState.currentRound()).isEqualTo(1);
        assertThat(updatedRoomState.totalRounds()).isEqualTo(3);
        assertThat(Duration.between(updatedRoomState.roundStartedAt(), updatedRoomState.roundDeadlineAt()))
            .isEqualTo(Duration.ofSeconds(updatedRoomState.timeLimitSeconds()));
        assertThat(updatedRoomState.gameStartedAt()).isEqualTo(updatedRoomState.roundStartedAt());
        assertThat(updatedRoomState.participants()).isEqualTo(roomState.participants());
        assertThat(updatedRoomState.createdAt()).isEqualTo(roomState.createdAt());
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomState);
    }

    /**
     * 방장이 아닌 참여자는 플립북 게임을 시작할 수 없습니다.
     */
    @Test
    void startRoomRejectsNonHostParticipant() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        AppUser participantUser = appUserWithNickname(participantUuid, "다현");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0),
            participant(participantUuid, "다현", false, 1));
        given(anonymousUserResolver.resolve(participantUuid.toString())).willReturn(participantUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomStartUseCase.startRoom(participantUuid.toString(), ROOM_CODE))
            .isInstanceOf(ForbiddenException.class).hasMessage("방장만 사용할 수 있습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 최소 시작 인원보다 적은 방은 시작할 수 없습니다.
     */
    @Test
    void startRoomRejectsNotEnoughParticipants() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomStartUseCase.startRoom(hostUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage("최소 2명이 모여야 시작할 수 있습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 참여자는 Redis 참여자로 등록되어 있어도 WebSocket 연결 전이면 게임 시작 대상이 될 수 없습니다.
     */
    @Test
    void startRoomRejectsParticipantRegisteredWithoutWebSocketConnection() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0),
            participant(participantUuid, "다현", false, 1, false, null));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomStartUseCase.startRoom(hostUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage("모든 참여자가 웹소켓에 연결되어야 게임을 시작할 수 있습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 게임 시작 저장 중 충돌이 나면 최신 방 상태를 다시 읽고 그 시점의 참여자 수로 라운드 수를 계산합니다.
     */
    @Test
    void startRoomRetriesOptimisticSaveConflictWithLatestRoomState() {
        UUID hostUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        UUID thirdUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState firstReadRoomState = roomState(FlipbookRoomStatus.WAITING, 45,
            participant(hostUuid, "망고", true, 0), participant(secondUuid, "다현", false, 1));
        FlipbookRoomState secondReadRoomState = roomState(FlipbookRoomStatus.WAITING, 45,
            participant(hostUuid, "망고", true, 0), participant(secondUuid, "다현", false, 1),
            participant(thirdUuid, "포도", false, 2));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(firstReadRoomState),
            Optional.of(secondReadRoomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false, true);

        FlipbookRoomStateResponse response = flipbookRoomStartUseCase.startRoom(hostUuid.toString(), ROOM_CODE);

        assertThat(response.status()).isEqualTo(FlipbookRoomStatus.PLAYING);
        assertThat(response.participantCount()).isEqualTo(3);
        assertThat(response.totalRounds()).isEqualTo(3);

        ArgumentCaptor<FlipbookRoomState> expectedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository, times(2)).saveIfUnchanged(expectedStateCaptor.capture(),
            updatedStateCaptor.capture());
        assertThat(expectedStateCaptor.getAllValues()).containsExactly(firstReadRoomState, secondReadRoomState);
        assertThat(updatedStateCaptor.getAllValues().get(1).participants())
            .isEqualTo(secondReadRoomState.participants());
        assertThat(updatedStateCaptor.getAllValues().get(1).totalRounds()).isEqualTo(3);
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedStateCaptor.getAllValues().get(1));
    }

    /**
     * 방장이 WAITING 방의 일반 참여자를 강퇴하면 참여자 목록에서 제거하고 강퇴 목록에 UUID를 기록합니다.
     */
    @Test
    void kickParticipantRemovesTargetAndStoresKickedUserUuid() {
        UUID hostUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        UUID remainingUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0),
            participant(targetUuid, "포도", false, 1), participant(remainingUuid, "사과", false, 3));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(anonymousUserResolver.parseUuid(targetUuid.toString())).willReturn(targetUuid);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomKickResponse response = flipbookRoomKickUseCase.kickParticipant(hostUuid.toString(), ROOM_CODE,
            targetUuid.toString());

        assertThat(response.kickedUserUuid()).isEqualTo(targetUuid.toString());
        assertThat(response.kickedNickname()).isEqualTo("포도");
        assertThat(response.participantCount()).isEqualTo(2);

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());
        FlipbookRoomState updatedRoomState = updatedStateCaptor.getValue();
        assertThat(updatedRoomState.participants()).extracting(FlipbookRoomParticipant::userUuid)
            .containsExactly(hostUuid.toString(), remainingUuid.toString());
        assertThat(updatedRoomState.participants()).extracting(FlipbookRoomParticipant::joinOrder).containsExactly(0,
            3);
        assertThat(updatedRoomState.kickedUserUuids()).containsExactly(targetUuid.toString());
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomState);
    }

    /**
     * 방장이 아닌 참여자는 강퇴할 수 없습니다.
     */
    @Test
    void kickParticipantRejectsNonHostParticipant() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        AppUser participantUser = appUserWithNickname(participantUuid, "다현");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0),
            participant(participantUuid, "다현", false, 1), participant(targetUuid, "포도", false, 2));
        given(anonymousUserResolver.resolve(participantUuid.toString())).willReturn(participantUser);
        given(anonymousUserResolver.parseUuid(targetUuid.toString())).willReturn(targetUuid);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(
            () -> flipbookRoomKickUseCase.kickParticipant(participantUuid.toString(), ROOM_CODE, targetUuid.toString()))
            .isInstanceOf(ForbiddenException.class).hasMessage("방장만 사용할 수 있는 기능입니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 방장은 자기 자신을 강퇴할 수 없습니다.
     */
    @Test
    void kickParticipantRejectsSelfKick() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(anonymousUserResolver.parseUuid(hostUuid.toString())).willReturn(hostUuid);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(
            () -> flipbookRoomKickUseCase.kickParticipant(hostUuid.toString(), ROOM_CODE, hostUuid.toString()))
            .isInstanceOf(ConflictException.class).hasMessage("자기 자신은 강퇴할 수 없습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 일반 참여자가 WAITING 방에서 퇴장하면 참여자 목록에서 제거하고 방장은 그대로 유지합니다.
     */
    @Test
    void leaveRoomParticipantRemovesRequesterAndKeepsHost() {
        UUID hostUuid = UUID.randomUUID();
        UUID leaverUuid = UUID.randomUUID();
        AppUser leaverUser = appUserWithNickname(leaverUuid, "포도");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0),
            participant(leaverUuid, "포도", false, 1));
        given(anonymousUserResolver.resolve(leaverUuid.toString())).willReturn(leaverUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomLeaveResponse response = flipbookRoomLeaveUseCase.leaveRoom(leaverUuid.toString(), ROOM_CODE);

        assertThat(response.leftUserUuid()).isEqualTo(leaverUuid.toString());
        assertThat(response.leftNickname()).isEqualTo("포도");
        assertThat(response.participantCount()).isEqualTo(1);
        assertThat(response.hostChanged()).isFalse();
        assertThat(response.roomClosed()).isFalse();
        assertThat(response.roomStatus()).isEqualTo(FlipbookRoomStatus.WAITING);

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());
        FlipbookRoomState updatedRoomState = updatedStateCaptor.getValue();
        assertThat(updatedRoomState.hostUserUuid()).isEqualTo(hostUuid.toString());
        assertThat(updatedRoomState.participants()).extracting(FlipbookRoomParticipant::userUuid)
            .containsExactly(hostUuid.toString());
        assertThat(updatedRoomState.kickedUserUuids()).isEmpty();
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomState);
    }

    /**
     * 방장이 WAITING 방에서 퇴장하면 joinOrder가 가장 작은 남은 참여자에게 방장을 승계합니다.
     */
    @Test
    void leaveRoomHostTransfersHostToLowestJoinOrderParticipant() {
        UUID hostUuid = UUID.randomUUID();
        UUID laterParticipantUuid = UUID.randomUUID();
        UUID newHostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0),
            participant(laterParticipantUuid, "사과", false, 3), participant(newHostUuid, "포도", false, 1));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomLeaveResponse response = flipbookRoomLeaveUseCase.leaveRoom(hostUuid.toString(), ROOM_CODE);

        assertThat(response.hostChanged()).isTrue();
        assertThat(response.newHostUserUuid()).isEqualTo(newHostUuid.toString());
        assertThat(response.newHostNickname()).isEqualTo("포도");

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());
        FlipbookRoomState updatedRoomState = updatedStateCaptor.getValue();
        assertThat(updatedRoomState.hostUserUuid()).isEqualTo(newHostUuid.toString());
        assertThat(updatedRoomState.participants()).extracting(FlipbookRoomParticipant::userUuid)
            .containsExactly(laterParticipantUuid.toString(), newHostUuid.toString());
        assertThat(updatedRoomState.participants()).extracting(FlipbookRoomParticipant::host).containsExactly(false,
            true);
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomState);
    }

    /**
     * 마지막 참여자가 퇴장하면 방은 CLOSED가 되고 cleanup은 기존 TTL에 맡깁니다.
     */
    @Test
    void leaveRoomLastParticipantClosesRoom() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomLeaveResponse response = flipbookRoomLeaveUseCase.leaveRoom(hostUuid.toString(), ROOM_CODE);

        assertThat(response.roomClosed()).isTrue();
        assertThat(response.roomStatus()).isEqualTo(FlipbookRoomStatus.CLOSED);
        assertThat(response.participantCount()).isZero();

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());
        FlipbookRoomState updatedRoomState = updatedStateCaptor.getValue();
        assertThat(updatedRoomState.status()).isEqualTo(FlipbookRoomStatus.CLOSED);
        assertThat(updatedRoomState.hostUserUuid()).isNull();
        assertThat(updatedRoomState.participants()).isEmpty();
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomState);
    }

    /**
     * 게임 시작 이후 상태에서는 자발적 퇴장 API를 사용할 수 없습니다.
     */
    @Test
    void leaveRoomRejectsNonWaitingRoom() {
        UUID hostUuid = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.PLAYING, 45, participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomLeaveUseCase.leaveRoom(hostUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage("대기실에서만 퇴장할 수 있습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    private FlipbookRoomState roomState(FlipbookRoomStatus status, int timeLimitSeconds,
        FlipbookRoomParticipant... participants) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new FlipbookRoomState(ROOM_CODE, status, participants[0].userUuid(), timeLimitSeconds, 2, 6,
            List.of(participants), now, now);
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return participant(userUuid, nickname, host, joinOrder, true, null);
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder,
        boolean connected, LocalDateTime disconnectedAt) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, joinOrder, connected, disconnectedAt,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    private AppUser appUserWithNickname(UUID userUuid, String nickname) {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt);
        appUser.updateNickname(nickname, createdAt.plusHours(1));

        return appUser;
    }
}
