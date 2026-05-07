package com.nemonicworld.flipbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.invite.repository.InviteRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 플립북 방 Redis TTL 갱신과 공통 초대코드 메타데이터 동기화를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class FlipbookInviteMetadataSyncServiceTest {

    private static final String ROOM_CODE = "FB3K9Q";

    @Mock
    private InviteRepository inviteRepository;

    /**
     * 기존 초대코드가 있으면 방 이름 등 기존 메타데이터는 유지하고 expiresAt만 최신 방 TTL에 맞춥니다.
     */
    @Test
    void syncWithRoomStateRefreshesExistingInviteExpiresAt() {
        FlipbookRoomState roomState = roomState(participant(UUID.randomUUID(), "망고", true));
        InviteMetadata existingInvite = new InviteMetadata(ROOM_CODE, "flipbook", ROOM_CODE, "처음 만든 플립북",
            roomState.updatedAt().minusMinutes(1));
        FlipbookInviteMetadataSyncService syncService = new FlipbookInviteMetadataSyncService(inviteRepository);
        given(inviteRepository.findByInviteCode(ROOM_CODE)).willReturn(Optional.of(existingInvite));

        syncService.syncWithRoomState(roomState);

        ArgumentCaptor<InviteMetadata> inviteCaptor = ArgumentCaptor.forClass(InviteMetadata.class);
        verify(inviteRepository).save(inviteCaptor.capture(), eq(FlipbookRoomRepository.ROOM_STATE_TTL));
        InviteMetadata refreshedInvite = inviteCaptor.getValue();
        assertThat(refreshedInvite.inviteCode()).isEqualTo(existingInvite.inviteCode());
        assertThat(refreshedInvite.boothType()).isEqualTo(existingInvite.boothType());
        assertThat(refreshedInvite.roomId()).isEqualTo(existingInvite.roomId());
        assertThat(refreshedInvite.roomName()).isEqualTo(existingInvite.roomName());
        assertThat(refreshedInvite.expiresAt())
            .isEqualTo(roomState.updatedAt().plus(FlipbookRoomRepository.ROOM_STATE_TTL));
    }

    /**
     * invite key가 이미 사라졌더라도 방이 살아 있으면 현재 방장 정보로 초대 메타데이터를 복구합니다.
     */
    @Test
    void syncWithRoomStateRestoresMissingInviteFromHostParticipant() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = roomState(participant(hostUuid, "다현", true));
        FlipbookInviteMetadataSyncService syncService = new FlipbookInviteMetadataSyncService(inviteRepository);
        given(inviteRepository.findByInviteCode(ROOM_CODE)).willReturn(Optional.empty());

        syncService.syncWithRoomState(roomState);

        ArgumentCaptor<InviteMetadata> inviteCaptor = ArgumentCaptor.forClass(InviteMetadata.class);
        verify(inviteRepository).save(inviteCaptor.capture(), eq(FlipbookRoomRepository.ROOM_STATE_TTL));
        InviteMetadata restoredInvite = inviteCaptor.getValue();
        assertThat(restoredInvite.inviteCode()).isEqualTo(ROOM_CODE);
        assertThat(restoredInvite.boothType()).isEqualTo("flipbook");
        assertThat(restoredInvite.roomId()).isEqualTo(ROOM_CODE);
        assertThat(restoredInvite.roomName()).isEqualTo("다현의 플립북");
        assertThat(restoredInvite.expiresAt())
            .isEqualTo(roomState.updatedAt().plus(FlipbookRoomRepository.ROOM_STATE_TTL));
    }

    /**
     * 닫힌 방처럼 참여자 정보가 없어 복구할 방장 이름이 없으면 새 invite key를 만들지 않습니다.
     */
    @Test
    void syncWithRoomStateSkipsRestoreWhenInviteAndHostAreMissing() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomState roomState = new FlipbookRoomState(ROOM_CODE, FlipbookRoomStatus.CLOSED, null, 45, 2, 6,
            List.of(), now.minusMinutes(5), now);
        FlipbookInviteMetadataSyncService syncService = new FlipbookInviteMetadataSyncService(inviteRepository);
        given(inviteRepository.findByInviteCode(ROOM_CODE)).willReturn(Optional.empty());

        syncService.syncWithRoomState(roomState);

        verify(inviteRepository, never()).save(any(), eq(FlipbookRoomRepository.ROOM_STATE_TTL));
    }

    private FlipbookRoomState roomState(FlipbookRoomParticipant participant) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new FlipbookRoomState(ROOM_CODE, FlipbookRoomStatus.WAITING, participant.userUuid(), 45, 2, 6,
            List.of(participant), now.minusMinutes(5), now);
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, 0, false, null, now);
    }
}
