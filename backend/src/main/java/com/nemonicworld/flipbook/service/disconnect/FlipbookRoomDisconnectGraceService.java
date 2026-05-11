package com.nemonicworld.flipbook.service.disconnect;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomMutationLockRepository;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.service.FlipbookRoomPolicy;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 게임 중 재접속 유예가 끝난 플립북 참여자를 이탈 확정하고, 필요하면 방장을 승계합니다.
 */
@Service
public class FlipbookRoomDisconnectGraceService {

    private static final Logger log = LoggerFactory.getLogger(FlipbookRoomDisconnectGraceService.class);

    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomMutationLockRepository flipbookRoomMutationLockRepository;
    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;
    private final Duration reconnectGrace;
    private final Duration roomMutationLockTtl;
    private final int scanLimit;

    public FlipbookRoomDisconnectGraceService(FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRoomMutationLockRepository flipbookRoomMutationLockRepository,
        FlipbookRoomEventPublisher flipbookRoomEventPublisher,
        FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService,
        @Value("${nemonic.flipbook.disconnect.reconnect-grace-seconds:"
            + FlipbookRoomPolicy.DEFAULT_RECONNECT_GRACE_SECONDS + "}") long reconnectGraceSeconds,
        @Value("${nemonic.flipbook.disconnect.scan-limit:100}") int scanLimit,
        @Value("${nemonic.flipbook.room-mutation-lock-ttl-ms:5000}") long roomMutationLockTtlMs) {
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomMutationLockRepository = flipbookRoomMutationLockRepository;
        this.flipbookRoomEventPublisher = flipbookRoomEventPublisher;
        this.flipbookInviteMetadataSyncService = flipbookInviteMetadataSyncService;
        this.reconnectGrace = Duration.ofSeconds(Math.max(0L, reconnectGraceSeconds));
        this.roomMutationLockTtl = Duration.ofMillis(Math.max(1L, roomMutationLockTtlMs));
        this.scanLimit = scanLimit;
    }

    /**
     * 처리 후보 PLAYING 방을 스캔하고 각 방을 독립적으로 처리합니다.
     */
    public FlipbookDisconnectGraceProcessResult processDroppedParticipants() {
        return processDroppedParticipants(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * 테스트에서 시간을 고정할 수 있도록 현재 시각을 주입받아 스캔합니다.
     */
    public FlipbookDisconnectGraceProcessResult processDroppedParticipants(LocalDateTime now) {
        LocalDateTime processedAt = now.truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime disconnectCutoff = processedAt.minus(reconnectGrace);
        List<FlipbookRoomState> candidateRooms = flipbookRoomRepository
            .findPlayingRoomsForDisconnectGrace(disconnectCutoff, scanLimit);
        int processedRoomCount = 0;
        int droppedParticipantCount = 0;

        for (FlipbookRoomState candidateRoom : candidateRooms) {
            try {
                FlipbookDisconnectGraceRoomResult result = processRoom(candidateRoom.roomCode(), processedAt);
                if (result.processed()) {
                    processedRoomCount++;
                    droppedParticipantCount += result.droppedParticipants().size();
                }
            } catch (RuntimeException e) {
                log.warn("플립북 방 이탈 확정 처리 중 오류가 발생했습니다. roomCode={}", candidateRoom.roomCode(), e);
            }
        }

        return new FlipbookDisconnectGraceProcessResult(candidateRooms.size(), processedRoomCount,
            droppedParticipantCount);
    }

    /**
     * 방 하나를 최신 Redis 상태 기준으로 재확인한 뒤 CAS로 저장합니다.
     */
    public FlipbookDisconnectGraceRoomResult processRoom(String roomCode, LocalDateTime now) {
        LocalDateTime processedAt = now.truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomState candidateRoomState = flipbookRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (candidateRoomState == null || candidateRoomState.status() != FlipbookRoomStatus.PLAYING
            || candidateRoomState.currentRound() == null) {
            return FlipbookDisconnectGraceRoomResult.noOp(roomCode);
        }

        String roomMutationLockToken = createRoomMutationLockToken("disconnect-grace", roomCode);
        boolean locked = flipbookRoomMutationLockRepository.acquireRoomMutationLock(roomCode, roomMutationLockToken,
            roomMutationLockTtl);
        if (!locked) {
            log.warn("플립북 이탈 확정 처리를 건너뜁니다. 방 상태 변경 잠금이 사용 중입니다. roomCode={}", roomCode);
            return FlipbookDisconnectGraceRoomResult.noOp(roomCode);
        }

        try {
            FlipbookDisconnectGraceRoomResult result = processRoomWithLock(roomCode, processedAt);
            publishDisconnectGraceEvents(result);

            return result;
        } finally {
            flipbookRoomMutationLockRepository.releaseRoomMutationLock(roomCode, roomMutationLockToken);
        }
    }

    private FlipbookDisconnectGraceRoomResult processRoomWithLock(String roomCode, LocalDateTime processedAt) {
        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            FlipbookRoomState roomState = flipbookRoomRepository.findByRoomCode(roomCode).orElse(null);
            if (roomState == null || roomState.status() != FlipbookRoomStatus.PLAYING
                || roomState.currentRound() == null) {
                return FlipbookDisconnectGraceRoomResult.noOp(roomCode);
            }

            ParticipantDropUpdate participantDropUpdate = dropExpiredParticipants(roomState, processedAt);
            if (!participantDropUpdate.changed()) {
                return FlipbookDisconnectGraceRoomResult.noOp(roomCode);
            }

            FlipbookRoomState updatedRoomState = roomState.withParticipantsHostAndStatus(
                participantDropUpdate.participants(), participantDropUpdate.hostUserUuid(), roomState.status(),
                processedAt);

            if (flipbookRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                flipbookInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
                return new FlipbookDisconnectGraceRoomResult(roomCode, true,
                    participantDropUpdate.droppedParticipants(), participantDropUpdate.hostChange());
            }
        }

        throw new ConflictException(FlipbookRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private String createRoomMutationLockToken(String owner, String roomCode) {
        return "token=%s,requestedAt=%s,owner=%s,roomCode=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), owner, roomCode);
    }

    private ParticipantDropUpdate dropExpiredParticipants(FlipbookRoomState roomState, LocalDateTime droppedAt) {
        List<FlipbookRoomParticipant> participants = new ArrayList<>(roomState.participants().size());
        List<FlipbookDroppedParticipantResult> droppedParticipants = new ArrayList<>();
        boolean changed = false;

        for (FlipbookRoomParticipant participant : roomState.participants()) {
            if (shouldDrop(participant, droppedAt)) {
                FlipbookRoomParticipant droppedParticipant = participant.drop(droppedAt);
                participants.add(droppedParticipant);
                droppedParticipants.add(new FlipbookDroppedParticipantResult(roomState.roomCode(),
                    participant.userUuid(), participant.nickname(), participant.disconnectedAt(), droppedAt));
                changed = true;
            } else {
                participants.add(participant);
            }
        }

        HostTransferUpdate hostTransferUpdate = transferHostIfNeeded(roomState, participants, droppedAt);
        changed = changed || hostTransferUpdate.changed();

        return new ParticipantDropUpdate(hostTransferUpdate.participants(), changed, hostTransferUpdate.hostUserUuid(),
            droppedParticipants, hostTransferUpdate.hostChange());
    }

    private boolean shouldDrop(FlipbookRoomParticipant participant, LocalDateTime now) {
        return !participant.dropped() && !participant.connected() && participant.disconnectedAt() != null
            && !participant.disconnectedAt().plus(reconnectGrace).isAfter(now);
    }

    private HostTransferUpdate transferHostIfNeeded(FlipbookRoomState roomState,
        List<FlipbookRoomParticipant> participants, LocalDateTime changedAt) {
        Optional<FlipbookRoomParticipant> currentHost = participants.stream()
            .filter(participant -> participant.userUuid().equals(roomState.hostUserUuid()) || participant.host())
            .min(Comparator.comparingInt(FlipbookRoomParticipant::joinOrder));

        if (currentHost.isEmpty() || !currentHost.get().dropped()) {
            return new HostTransferUpdate(participants, roomState.hostUserUuid(), false, null);
        }

        Optional<FlipbookRoomParticipant> newHost = participants.stream()
            .filter(participant -> !participant.dropped() && participant.connected())
            .min(Comparator.comparingInt(FlipbookRoomParticipant::joinOrder));

        if (newHost.isEmpty()) {
            return new HostTransferUpdate(participants, roomState.hostUserUuid(), false, null);
        }

        FlipbookRoomParticipant newHostParticipant = newHost.get();
        List<FlipbookRoomParticipant> transferredParticipants = participants.stream()
            .map(participant -> participant.withHost(participant.userUuid().equals(newHostParticipant.userUuid())))
            .toList();
        FlipbookHostChangeResult hostChange = new FlipbookHostChangeResult(roomState.roomCode(),
            currentHost.get().userUuid(), newHostParticipant.userUuid(), newHostParticipant.nickname(), changedAt);

        return new HostTransferUpdate(transferredParticipants, newHostParticipant.userUuid(), true, hostChange);
    }

    private void publishDisconnectGraceEvents(FlipbookDisconnectGraceRoomResult result) {
        for (FlipbookDroppedParticipantResult droppedParticipant : result.droppedParticipants()) {
            flipbookRoomEventPublisher.publishParticipantDropped(droppedParticipant);
        }

        if (result.hostChange() != null) {
            flipbookRoomEventPublisher.publishHostChanged(result.hostChange());
        }
    }

    private record ParticipantDropUpdate(List<FlipbookRoomParticipant> participants, boolean changed,
        String hostUserUuid, List<FlipbookDroppedParticipantResult> droppedParticipants,
        FlipbookHostChangeResult hostChange) {
    }

    private record HostTransferUpdate(List<FlipbookRoomParticipant> participants, String hostUserUuid, boolean changed,
        FlipbookHostChangeResult hostChange) {
    }
}
