package com.nemonicworld.flipbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.flipbook.dto.request.FlipbookFrameSubmitRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookFrameSubmitResponse;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.game.FlipbookRoomRoundAdvanceService;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
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

/**
 * 플립북 프레임 제출 유스케이스의 Redis 상태 갱신 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class FlipbookFrameSubmitUseCaseTest {

    private static final String ROOM_CODE = "FB3K9Q";

    @Mock
    private AnonymousUserResolver anonymousUserResolver;

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    @Mock
    private FlipbookRoomRepository flipbookRoomRepository;

    @Mock
    private FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    @Mock
    private FileUploadRepository fileUploadRepository;

    private FlipbookFrameSubmitUseCase flipbookFrameSubmitUseCase;

    @BeforeEach
    void setUp() {
        FlipbookRoomPolicy flipbookRoomPolicy = new FlipbookRoomPolicy(roomCodeGenerator, flipbookRoomRepository);
        FlipbookFrameImageUrlResolver flipbookFrameImageUrlResolver = new FlipbookFrameImageUrlResolver(
            new MinioStorageProperties("http://minio:9000", "https://example.com/minio", "access", "secret", "nemonic",
                10, 10_485_760));
        flipbookFrameSubmitUseCase = new FlipbookFrameSubmitUseCase(anonymousUserResolver, flipbookRoomRepository,
            flipbookRoomPolicy, flipbookFrameImageUrlResolver, flipbookInviteMetadataSyncService,
            new FlipbookRoomRoundAdvanceService(), fileUploadRepository, 2000L);
    }

    /**
     * 현재 라운드의 마지막 프레임이 제출되면 다음 라운드로 넘어갑니다.
     */
    @Test
    void submitFrameStoresFileAndAdvancesNextRoundWhenCurrentRoundCompleted() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FileUpload fileUpload = uploadedFile(fileId, hostUuid, FileUploadPurpose.FLIPBOOK);
        FlipbookRoomState roomState = playingRoomState(hostUuid, participantUuid, 1, 2,
            List.of(assignment(0, 0, 1, hostUuid, FlipbookFrameAssignmentStatus.PENDING, null, null, null),
                assignment(1, 0, 1, participantUuid, FlipbookFrameAssignmentStatus.SUBMITTED, UUID.randomUUID(),
                    "uploads/flipbook/already.png", LocalDateTime.now().minusSeconds(5)),
                assignment(0, 1, 2, participantUuid, FlipbookFrameAssignmentStatus.PENDING, null, null, null),
                assignment(1, 1, 2, hostUuid, FlipbookFrameAssignmentStatus.PENDING, null, null, null)));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(fileUploadRepository.findById(fileId)).willReturn(Optional.of(fileUpload));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookFrameSubmitResponse response = flipbookFrameSubmitUseCase.submitFrame(hostUuid.toString(), ROOM_CODE, 1,
            new FlipbookFrameSubmitRequest(0, 0, fileId.toString()));

        assertThat(response.assignmentStatus()).isEqualTo(FlipbookFrameAssignmentStatus.SUBMITTED);
        assertThat(response.fileId()).isEqualTo(fileId.toString());
        assertThat(response.objectKey()).isEqualTo(fileUpload.getObjectKey());
        assertThat(response.alreadySubmitted()).isFalse();
        assertThat(response.currentRoundCompleted()).isTrue();
        assertThat(response.submittedCount()).isEqualTo(2);
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.advanced()).isTrue();
        assertThat(response.nextRound()).isEqualTo(2);
        assertThat(response.roomStatus()).isEqualTo(FlipbookRoomStatus.PLAYING);

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());
        FlipbookRoomState updatedRoomState = updatedStateCaptor.getValue();
        assertThat(updatedRoomState.currentRound()).isEqualTo(2);
        assertThat(updatedRoomState.roundDeadlineAt())
            .isEqualTo(updatedRoomState.roundStartedAt().plusSeconds(updatedRoomState.timeLimitSeconds()));
        assertThat(updatedRoomState.assignments().get(0).fileId()).isEqualTo(fileId.toString());
        assertThat(updatedRoomState.assignments().get(0).objectKey()).isEqualTo(fileUpload.getObjectKey());
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomState);
    }

    /**
     * 마지막 라운드가 모두 제출되면 방 상태를 FINALIZING으로 전환합니다.
     */
    @Test
    void submitFrameFinishesRoomWhenLastRoundCompleted() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FileUpload fileUpload = uploadedFile(fileId, hostUuid, FileUploadPurpose.FLIPBOOK);
        FlipbookRoomState roomState = playingRoomState(hostUuid, participantUuid, 2, 2,
            List.of(assignment(0, 1, 2, hostUuid, FlipbookFrameAssignmentStatus.PENDING, null, null, null),
                assignment(1, 1, 2, participantUuid, FlipbookFrameAssignmentStatus.SUBMITTED, UUID.randomUUID(),
                    "uploads/flipbook/already.png", LocalDateTime.now().minusSeconds(5))));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(fileUploadRepository.findById(fileId)).willReturn(Optional.of(fileUpload));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookFrameSubmitResponse response = flipbookFrameSubmitUseCase.submitFrame(hostUuid.toString(), ROOM_CODE, 2,
            new FlipbookFrameSubmitRequest(0, 1, fileId.toString()));

        assertThat(response.currentRoundCompleted()).isTrue();
        assertThat(response.advanced()).isTrue();
        assertThat(response.nextRound()).isNull();
        assertThat(response.allRoundsCompleted()).isTrue();
        assertThat(response.roomStatus()).isEqualTo(FlipbookRoomStatus.FINALIZING);

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());
        assertThat(updatedStateCaptor.getValue().status()).isEqualTo(FlipbookRoomStatus.FINALIZING);
    }

    /**
     * 이미 제출된 배정에 대한 같은 요청은 Redis를 다시 갱신하지 않고 멱등 응답합니다.
     */
    @Test
    void submitFrameReturnsAlreadySubmittedWhenAssignmentWasSubmitted() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FileUpload fileUpload = uploadedFile(fileId, hostUuid, FileUploadPurpose.FLIPBOOK);
        LocalDateTime submittedAt = LocalDateTime.now().minusSeconds(3).truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomState roomState = playingRoomState(hostUuid, participantUuid, 1, 2, List.of(assignment(0, 0, 1,
            hostUuid, FlipbookFrameAssignmentStatus.SUBMITTED, fileId, fileUpload.getObjectKey(), submittedAt)));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(fileUploadRepository.findById(fileId)).willReturn(Optional.of(fileUpload));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        FlipbookFrameSubmitResponse response = flipbookFrameSubmitUseCase.submitFrame(hostUuid.toString(), ROOM_CODE, 1,
            new FlipbookFrameSubmitRequest(0, 0, fileId.toString()));

        assertThat(response.alreadySubmitted()).isTrue();
        assertThat(response.submittedAt()).isEqualTo(submittedAt);
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 라운드 마감 직후 자동 제출 유예 시간 안에서는 프론트가 현재 캔버스를 제출할 수 있습니다.
     */
    @Test
    void submitFrameAllowsSubmissionDuringAutoSubmitGracePeriod() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FileUpload fileUpload = uploadedFile(fileId, hostUuid, FileUploadPurpose.FLIPBOOK);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomState roomState = playingRoomState(hostUuid, participantUuid, 1, 2, now.minusSeconds(45),
            now.minusSeconds(1),
            List.of(assignment(0, 0, 1, hostUuid, FlipbookFrameAssignmentStatus.PENDING, null, null, null)));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(fileUploadRepository.findById(fileId)).willReturn(Optional.of(fileUpload));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookFrameSubmitResponse response = flipbookFrameSubmitUseCase.submitFrame(hostUuid.toString(), ROOM_CODE, 1,
            new FlipbookFrameSubmitRequest(0, 0, fileId.toString()));

        assertThat(response.assignmentStatus()).isEqualTo(FlipbookFrameAssignmentStatus.SUBMITTED);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class));
    }

    /**
     * 자동 제출 유예 시간까지 지난 뒤에는 수동 제출을 거부합니다.
     */
    @Test
    void submitFrameRejectsSubmissionAfterAutoSubmitGraceExpired() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FileUpload fileUpload = uploadedFile(fileId, hostUuid, FileUploadPurpose.FLIPBOOK);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomState roomState = playingRoomState(hostUuid, participantUuid, 1, 2, now.minusSeconds(45),
            now.minusSeconds(3),
            List.of(assignment(0, 0, 1, hostUuid, FlipbookFrameAssignmentStatus.PENDING, null, null, null)));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(fileUploadRepository.findById(fileId)).willReturn(Optional.of(fileUpload));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookFrameSubmitUseCase.submitFrame(hostUuid.toString(), ROOM_CODE, 1,
            new FlipbookFrameSubmitRequest(0, 0, fileId.toString()))).isInstanceOf(ConflictException.class)
            .hasMessage("제출 시간이 만료되었습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 자동 제출 처리된 프레임은 사용자가 다시 수동 제출할 수 없습니다.
     */
    @Test
    void submitFrameRejectsAutoSubmittedAssignment() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FileUpload fileUpload = uploadedFile(fileId, hostUuid, FileUploadPurpose.FLIPBOOK);
        FlipbookRoomState roomState = playingRoomState(hostUuid, participantUuid, 1, 2, List.of(assignment(0, 0, 1,
            hostUuid, FlipbookFrameAssignmentStatus.AUTO_SUBMITTED, null, null, LocalDateTime.now().minusSeconds(3))));
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(fileUploadRepository.findById(fileId)).willReturn(Optional.of(fileUpload));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookFrameSubmitUseCase.submitFrame(hostUuid.toString(), ROOM_CODE, 1,
            new FlipbookFrameSubmitRequest(0, 0, fileId.toString()))).isInstanceOf(ConflictException.class)
            .hasMessage("이미 자동 제출 처리되었습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 다른 사용자가 업로드한 fileId는 제출에 사용할 수 없습니다.
     */
    @Test
    void submitFrameRejectsFileOwnedByAnotherUser() {
        UUID hostUuid = UUID.randomUUID();
        UUID anotherUserUuid = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FileUpload fileUpload = uploadedFile(fileId, anotherUserUuid, FileUploadPurpose.FLIPBOOK);
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(fileUploadRepository.findById(fileId)).willReturn(Optional.of(fileUpload));

        assertThatThrownBy(() -> flipbookFrameSubmitUseCase.submitFrame(hostUuid.toString(), ROOM_CODE, 1,
            new FlipbookFrameSubmitRequest(0, 0, fileId.toString()))).isInstanceOf(ForbiddenException.class)
            .hasMessage("파일에 접근할 권한이 없습니다.");

        verify(flipbookRoomRepository, never()).findByRoomCode(any());
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    /**
     * 업로드 완료 상태가 아닌 fileId는 프레임 제출에 사용할 수 없습니다.
     */
    @Test
    void submitFrameRejectsPendingFileUpload() {
        UUID hostUuid = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AppUser hostUser = appUserWithNickname(hostUuid, "망고");
        FileUpload fileUpload = pendingFile(fileId, hostUuid, FileUploadPurpose.FLIPBOOK);
        given(anonymousUserResolver.resolve(hostUuid.toString())).willReturn(hostUser);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(fileUploadRepository.findById(fileId)).willReturn(Optional.of(fileUpload));

        assertThatThrownBy(() -> flipbookFrameSubmitUseCase.submitFrame(hostUuid.toString(), ROOM_CODE, 1,
            new FlipbookFrameSubmitRequest(0, 0, fileId.toString()))).isInstanceOf(ConflictException.class)
            .hasMessage("업로드 완료된 파일만 제출할 수 있습니다.");

        verify(flipbookRoomRepository, never()).findByRoomCode(any());
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    private FlipbookRoomState playingRoomState(UUID hostUuid, UUID participantUuid, int currentRound, int totalRounds,
        List<FlipbookFrameAssignment> assignments) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return playingRoomState(hostUuid, participantUuid, currentRound, totalRounds, now.minusSeconds(10),
            now.plusSeconds(35), assignments);
    }

    private FlipbookRoomState playingRoomState(UUID hostUuid, UUID participantUuid, int currentRound, int totalRounds,
        LocalDateTime roundStartedAt, LocalDateTime roundDeadlineAt, List<FlipbookFrameAssignment> assignments) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new FlipbookRoomState(ROOM_CODE, FlipbookRoomStatus.PLAYING, hostUuid.toString(), 45, 2, 6, currentRound,
            totalRounds, roundStartedAt, roundDeadlineAt, roundStartedAt, assignments,
            List.of(participant(hostUuid, "망고", true, 0), participant(participantUuid, "다현", false, 1)),
            now.minusMinutes(1), now.minusSeconds(10), List.of());
    }

    private FlipbookFrameAssignment assignment(int flipbookIndex, int frameIndex, int round, UUID assignedUserUuid,
        FlipbookFrameAssignmentStatus status, UUID fileId, String objectKey, LocalDateTime submittedAt) {
        return new FlipbookFrameAssignment(flipbookIndex, frameIndex, round, assignedUserUuid.toString(), status,
            fileId == null ? null : fileId.toString(), objectKey, false, false, submittedAt);
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private FileUpload uploadedFile(UUID fileId, UUID userUuid, FileUploadPurpose purpose) {
        FileUpload fileUpload = pendingFile(fileId, userUuid, purpose);
        fileUpload.markUploaded(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        return fileUpload;
    }

    private FileUpload pendingFile(UUID fileId, UUID userUuid, FileUploadPurpose purpose) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return FileUpload.createPending(fileId, userUuid, purpose, "frame.png", "image/png", 1024L,
            "uploads/flipbook/2026/05/08/%s/frame.png".formatted(fileId), now.plusMinutes(10), now);
    }

    private AppUser appUserWithNickname(UUID userUuid, String nickname) {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt);
        appUser.updateNickname(nickname, createdAt.plusHours(1));

        return appUser;
    }
}
