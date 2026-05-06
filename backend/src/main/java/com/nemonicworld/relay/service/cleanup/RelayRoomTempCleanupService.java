package com.nemonicworld.relay.service.cleanup;

import com.nemonicworld.relay.entity.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * CLOSED 릴레이 방의 임시 이미지를 MinIO에서 정리합니다.
 */
@Service
public class RelayRoomTempCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomTempCleanupService.class);

    private final RelayRoomRepository relayRoomRepository;
    private final RelayTempFileStorage relayTempFileStorage;
    private final int scanLimit;
    private final Duration markerTtl;
    private final Duration lockTtl;
    private final Duration oldTempThreshold;
    private final int oldTempScanLimit;

    public RelayRoomTempCleanupService(RelayRoomRepository relayRoomRepository,
        RelayTempFileStorage relayTempFileStorage, @Value("${nemonic.relay.cleanup.scan-limit:100}") int scanLimit,
        @Value("${nemonic.relay.cleanup.marker-ttl-hours:24}") long markerTtlHours,
        @Value("${nemonic.relay.cleanup.lock-ttl-seconds:60}") long lockTtlSeconds,
        @Value("${nemonic.relay.cleanup.old-temp-threshold-hours:24}") long oldTempThresholdHours,
        @Value("${nemonic.relay.cleanup.old-temp-scan-limit:1000}") int oldTempScanLimit) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayTempFileStorage = relayTempFileStorage;
        this.scanLimit = scanLimit;
        this.markerTtl = Duration.ofHours(Math.max(1L, markerTtlHours));
        this.lockTtl = Duration.ofSeconds(Math.max(1L, lockTtlSeconds));
        this.oldTempThreshold = Duration.ofHours(Math.max(1L, oldTempThresholdHours));
        this.oldTempScanLimit = oldTempScanLimit;
    }

    /**
     * CLOSED 방을 스캔해서 cleanup이 끝나지 않은 방만 정리합니다.
     */
    public RelayTempCleanupProcessResult cleanupClosedRooms() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return cleanupClosedRooms(now);
    }

    /**
     * 테스트에서 시간을 고정할 수 있도록 now를 주입받아 cleanup을 실행합니다.
     */
    public RelayTempCleanupProcessResult cleanupClosedRooms(LocalDateTime now) {
        LocalDateTime cleanedAt = now.truncatedTo(ChronoUnit.SECONDS);
        List<RelayRoomState> closedRooms = relayRoomRepository.findClosedRooms(scanLimit);
        int cleanedRoomCount = 0;
        int deletedObjectCount = 0;

        for (RelayRoomState closedRoom : closedRooms) {
            try {
                RelayRoomTempCleanupResult result = cleanupClosedRoom(closedRoom.roomCode(), cleanedAt);
                if (result.cleaned()) {
                    cleanedRoomCount++;
                    deletedObjectCount += result.deletedObjectCount();
                }
            } catch (RuntimeException e) {
                log.warn("릴레이 방 임시 파일 정리에 실패했습니다. roomCode={}", closedRoom.roomCode(), e);
            }
        }

        return new RelayTempCleanupProcessResult(closedRooms.size(), cleanedRoomCount, deletedObjectCount);
    }

    /**
     * 특정 CLOSED 방의 assignment에 남아 있는 relay/tmp/{roomCode}/ 파일만 삭제합니다.
     */
    public RelayRoomTempCleanupResult cleanupClosedRoom(String roomCode, LocalDateTime now) {
        LocalDateTime cleanedAt = now.truncatedTo(ChronoUnit.SECONDS);
        if (relayRoomRepository.isTempCleanupMarked(roomCode)) {
            return RelayRoomTempCleanupResult.noOp(roomCode);
        }

        if (!relayRoomRepository.acquireTempCleanupLock(roomCode, lockTtl)) {
            return RelayRoomTempCleanupResult.noOp(roomCode);
        }

        try {
            if (relayRoomRepository.isTempCleanupMarked(roomCode)) {
                return RelayRoomTempCleanupResult.noOp(roomCode);
            }

            RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
            if (roomState == null || roomState.status() != RelayRoomStatus.CLOSED) {
                return RelayRoomTempCleanupResult.noOp(roomCode);
            }

            List<String> objectKeys = collectTempObjectKeys(roomState);
            if (!objectKeys.isEmpty()) {
                relayTempFileStorage.deleteObjects(objectKeys);
            }

            relayRoomRepository.markTempCleanup(roomCode, cleanedAt, markerTtl);

            return RelayRoomTempCleanupResult.cleaned(roomCode, objectKeys.size());
        } finally {
            releaseCleanupLock(roomCode);
        }
    }

    /**
     * Redis room state를 잃은 경우를 대비해 24시간 이상 지난 relay/tmp object를 fallback으로 정리합니다.
     */
    public RelayOldTempCleanupResult cleanupOldTempObjects() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return cleanupOldTempObjects(now);
    }

    /**
     * 테스트에서 시간을 고정할 수 있도록 now를 주입받아 오래된 relay/tmp object를 정리합니다.
     */
    public RelayOldTempCleanupResult cleanupOldTempObjects(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(oldTempThreshold).truncatedTo(ChronoUnit.SECONDS);
        List<String> oldTempObjectKeys;
        try {
            oldTempObjectKeys = relayTempFileStorage.findOldTempObjectKeys(cutoff, oldTempScanLimit);
        } catch (RuntimeException e) {
            log.warn("오래된 릴레이 임시 파일 조회에 실패했습니다.", e);
            return new RelayOldTempCleanupResult(0, 0);
        }

        if (oldTempObjectKeys.isEmpty()) {
            return new RelayOldTempCleanupResult(0, 0);
        }

        try {
            relayTempFileStorage.deleteObjects(oldTempObjectKeys);
        } catch (RuntimeException e) {
            log.warn("오래된 릴레이 임시 파일 삭제에 실패했습니다.", e);
            return new RelayOldTempCleanupResult(oldTempObjectKeys.size(), 0);
        }

        return new RelayOldTempCleanupResult(oldTempObjectKeys.size(), oldTempObjectKeys.size());
    }

    private List<String> collectTempObjectKeys(RelayRoomState roomState) {
        String allowedPrefix = "relay/tmp/%s/".formatted(roomState.roomCode());
        Set<String> objectKeys = new LinkedHashSet<>();

        for (RelayRoomAssignment assignment : roomState.assignments()) {
            addIfRelayTempObjectKey(objectKeys, assignment.objectKey(), allowedPrefix);
            addIfRelayTempObjectKey(objectKeys, assignment.hintObjectKey(), allowedPrefix);
        }

        return List.copyOf(objectKeys);
    }

    private void addIfRelayTempObjectKey(Set<String> objectKeys, String objectKey, String allowedPrefix) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }

        String trimmedObjectKey = objectKey.trim();
        if (trimmedObjectKey.startsWith(allowedPrefix)) {
            objectKeys.add(trimmedObjectKey);
        }
    }

    private void releaseCleanupLock(String roomCode) {
        try {
            relayRoomRepository.releaseTempCleanupLock(roomCode);
        } catch (RuntimeException e) {
            log.warn("릴레이 방 임시 파일 cleanup lock 해제에 실패했습니다. roomCode={}", roomCode, e);
        }
    }
}
