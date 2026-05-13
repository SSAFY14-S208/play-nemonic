package com.nemonicworld.flipbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.close.FlipbookRoomCloseCommand;
import com.nemonicworld.flipbook.service.close.FlipbookRoomCloseProcessResult;
import com.nemonicworld.flipbook.service.close.FlipbookRoomCloseResult;
import com.nemonicworld.flipbook.service.close.FlipbookRoomCloseService;
import com.nemonicworld.flipbook.service.support.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlipbookRoomCloseServiceTest {

    private static final String ROOM_CODE = "FB3K9Q";
    private static final String SECOND_ROOM_CODE = "FC4M8N";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 15, 5).truncatedTo(ChronoUnit.SECONDS);
    private static final long CLOSE_DELAY_SECONDS = 300L;
    private static final int SCAN_LIMIT = 10;

    @Mock
    private FlipbookRoomRepository flipbookRoomRepository;

    @Mock
    private FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    @Mock
    private FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    private FlipbookRoomCloseService flipbookRoomCloseService;

    @BeforeEach
    void setUp() {
        flipbookRoomCloseService = new FlipbookRoomCloseService(flipbookRoomRepository,
            new FlipbookRoomCloseCommand(flipbookRoomRepository, flipbookInviteMetadataSyncService),
            flipbookRoomEventPublisher, CLOSE_DELAY_SECONDS, SCAN_LIMIT);
    }

    @Test
    void closeRoomDoesNothingBeforeCloseDelay() {
        FlipbookRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusSeconds(CLOSE_DELAY_SECONDS - 1));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        FlipbookRoomCloseResult result = flipbookRoomCloseService.closeRoom(ROOM_CODE, NOW);

        assertThat(result.closed()).isFalse();
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(flipbookRoomEventPublisher);
    }

    @Test
    void closeFinishedRoomIfUnchangedClosesWithoutCloseDelay() {
        FlipbookRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusSeconds(CLOSE_DELAY_SECONDS - 1));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomCloseResult result = flipbookRoomCloseService.closeFinishedRoomIfUnchanged(roomState, NOW);

        assertThat(result.closed()).isTrue();
        assertThat(result.roomState().status()).isEqualTo(FlipbookRoomStatus.CLOSED);
        verify(flipbookRoomRepository).saveIfUnchanged(eq(roomState), any(FlipbookRoomState.class));
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(result.roomState());
        verify(flipbookRoomEventPublisher).publishRoomClosed(ROOM_CODE, NOW);
    }

    @Test
    void closeFinishedRoomIfUnchangedDoesNotPublishEventWhenRedisSaveConflicts() {
        FlipbookRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusMinutes(10));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false);

        FlipbookRoomCloseResult result = flipbookRoomCloseService.closeFinishedRoomIfUnchanged(roomState, NOW);

        assertThat(result.closed()).isFalse();
        verify(flipbookRoomRepository).saveIfUnchanged(eq(roomState), any(FlipbookRoomState.class));
        verifyNoInteractions(flipbookRoomEventPublisher, flipbookInviteMetadataSyncService);
    }

    @Test
    void closeRoomClosesFinishedRoomAfterCloseDelay() {
        FlipbookRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusSeconds(CLOSE_DELAY_SECONDS));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomCloseResult result = flipbookRoomCloseService.closeRoom(ROOM_CODE, NOW);

        assertThat(result.closed()).isTrue();
        assertThat(result.closedAt()).isEqualTo(NOW);
        assertThat(result.roomState().status()).isEqualTo(FlipbookRoomStatus.CLOSED);
        assertThat(result.roomState().updatedAt()).isEqualTo(NOW);

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(eq(roomState), updatedStateCaptor.capture());
        assertThat(updatedStateCaptor.getValue().status()).isEqualTo(FlipbookRoomStatus.CLOSED);
        verify(flipbookRoomEventPublisher).publishRoomClosed(ROOM_CODE, NOW);
    }

    @ParameterizedTest
    @EnumSource(value = FlipbookRoomStatus.class, names = {"WAITING", "PLAYING", "FINALIZING", "CLOSED"})
    void closeRoomIgnoresNonFinishedRooms(FlipbookRoomStatus roomStatus) {
        FlipbookRoomState roomState = room(ROOM_CODE, roomStatus, NOW.minusMinutes(10));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        FlipbookRoomCloseResult result = flipbookRoomCloseService.closeRoom(ROOM_CODE, NOW);

        assertThat(result.closed()).isFalse();
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(flipbookRoomEventPublisher);
    }

    @Test
    void closeRoomDoesNotPublishEventWhenRedisSaveKeepsConflicting() {
        FlipbookRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusMinutes(10));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> flipbookRoomCloseService.closeRoom(ROOM_CODE, NOW))
            .isInstanceOf(ConflictException.class);

        verify(flipbookRoomRepository, times(3)).saveIfUnchanged(any(FlipbookRoomState.class),
            any(FlipbookRoomState.class));
        verifyNoInteractions(flipbookRoomEventPublisher);
    }

    @Test
    void closeRoomRetriesRedisSaveConflictAndPublishesEventOnce() {
        FlipbookRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusMinutes(10));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false, true);

        FlipbookRoomCloseResult result = flipbookRoomCloseService.closeRoom(ROOM_CODE, NOW);

        assertThat(result.closed()).isTrue();
        verify(flipbookRoomRepository, times(2)).saveIfUnchanged(any(FlipbookRoomState.class),
            any(FlipbookRoomState.class));
        verify(flipbookRoomEventPublisher, times(1)).publishRoomClosed(ROOM_CODE, NOW);
    }

    @Test
    void closeRoomDoesNotDuplicateEventAfterAlreadyClosed() {
        FlipbookRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusMinutes(10));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);
        FlipbookRoomCloseResult firstResult = flipbookRoomCloseService.closeRoom(ROOM_CODE, NOW);
        clearInvocations(flipbookRoomRepository, flipbookRoomEventPublisher);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(firstResult.roomState()));

        FlipbookRoomCloseResult secondResult = flipbookRoomCloseService.closeRoom(ROOM_CODE, NOW.plusSeconds(1));

        assertThat(secondResult.closed()).isFalse();
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(flipbookRoomEventPublisher);
    }

    @Test
    void closeFinishedRoomsScansClosableRoomsAndClosesOnlySuccessfulRooms() {
        FlipbookRoomState firstRoom = finishedRoom(ROOM_CODE, NOW.minusMinutes(10));
        FlipbookRoomState secondRoom = finishedRoom(SECOND_ROOM_CODE, NOW.minusMinutes(10));
        given(flipbookRoomRepository.findClosableFinishedRooms(NOW.minusSeconds(CLOSE_DELAY_SECONDS), SCAN_LIMIT))
            .willReturn(List.of(firstRoom, secondRoom));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willThrow(new InternalServerException("boom"));
        given(flipbookRoomRepository.findByRoomCode(SECOND_ROOM_CODE)).willReturn(Optional.of(secondRoom));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomCloseProcessResult result = flipbookRoomCloseService.closeFinishedRooms(NOW);

        assertThat(result.scannedRoomCount()).isEqualTo(2);
        assertThat(result.closedRoomCount()).isEqualTo(1);
        verify(flipbookRoomEventPublisher).publishRoomClosed(SECOND_ROOM_CODE, NOW);
        verify(flipbookRoomEventPublisher, never()).publishRoomClosed(eq(ROOM_CODE), any(LocalDateTime.class));
    }

    private FlipbookRoomState finishedRoom(String roomCode, LocalDateTime updatedAt) {
        return room(roomCode, FlipbookRoomStatus.FINISHED, updatedAt);
    }

    private FlipbookRoomState room(String roomCode, FlipbookRoomStatus status, LocalDateTime updatedAt) {
        LocalDateTime createdAt = NOW.minusMinutes(20);
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomParticipant host = new FlipbookRoomParticipant(hostUuid.toString(), "Mango", true, 0, true, null,
            createdAt);

        return new FlipbookRoomState(roomCode, status, hostUuid.toString(), 45, 2, 6, 8, 8, NOW.minusMinutes(11),
            NOW.minusMinutes(10), NOW.minusMinutes(15), List.of(), List.of(host), createdAt, updatedAt, List.of());
    }
}
