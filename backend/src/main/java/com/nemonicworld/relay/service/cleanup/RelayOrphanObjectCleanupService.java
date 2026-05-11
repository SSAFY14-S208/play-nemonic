package com.nemonicworld.relay.service.cleanup;

import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayArtifactRepository;
import com.nemonicworld.relay.repository.RelayFinalizationAttemptRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

@Service
public class RelayOrphanObjectCleanupService {

    private static final String TEMP_TARGET = "temp";
    private static final String RESULT_TARGET = "result";
    private static final String RELAY_TEMP_OBJECT_KEY_PREFIX = "relay/tmp/";
    private static final String RELAY_RESULT_OBJECT_KEY_PREFIX = "relay/results/";

    private final RelayObjectStorage relayObjectStorage;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayArtifactRepository relayArtifactRepository;
    private final RelayFinalizationAttemptRepository relayFinalizationAttemptRepository;
    private final int scanLimit;
    private final Duration tempRetention;
    private final Duration resultRetention;

    public RelayOrphanObjectCleanupService(RelayObjectStorage relayObjectStorage,
        RelayRoomRepository relayRoomRepository, RelayArtifactRepository relayArtifactRepository,
        RelayFinalizationAttemptRepository relayFinalizationAttemptRepository,
        @Value("${nemonic.relay.orphan-cleanup.scan-limit:500}") int scanLimit,
        @Value("${nemonic.relay.orphan-cleanup.temp-retention-hours:24}") long tempRetentionHours,
        @Value("${nemonic.relay.orphan-cleanup.result-retention-hours:24}") long resultRetentionHours) {
        this.relayObjectStorage = relayObjectStorage;
        this.relayRoomRepository = relayRoomRepository;
        this.relayArtifactRepository = relayArtifactRepository;
        this.relayFinalizationAttemptRepository = relayFinalizationAttemptRepository;
        this.scanLimit = Math.max(1, scanLimit);
        this.tempRetention = Duration.ofHours(Math.max(1L, tempRetentionHours));
        this.resultRetention = Duration.ofHours(Math.max(1L, resultRetentionHours));
    }

    public RelayOrphanObjectCleanupProcessResult cleanupOrphanObjects() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new RelayOrphanObjectCleanupProcessResult(cleanupTempObjects(now), cleanupResultObjects(now));
    }

    public RelayOrphanObjectCleanupResult cleanupTempObjects(LocalDateTime now) {
        return cleanupObjects(TEMP_TARGET, RELAY_TEMP_OBJECT_KEY_PREFIX, now, tempRetention,
            this::isTempObjectSafeToDelete);
    }

    public RelayOrphanObjectCleanupResult cleanupResultObjects(LocalDateTime now) {
        return cleanupObjects(RESULT_TARGET, RELAY_RESULT_OBJECT_KEY_PREFIX, now, resultRetention,
            this::isResultObjectSafeToDelete);
    }

    private RelayOrphanObjectCleanupResult cleanupObjects(String target, String prefix, LocalDateTime now,
        Duration retention, ObjectDeletionPolicy deletionPolicy) {
        long startedNanos = System.nanoTime();
        LocalDateTime cutoff = now.minus(retention).truncatedTo(ChronoUnit.SECONDS);
        List<RelayStoredObject> objects = relayObjectStorage.findObjects(prefix, cutoff, scanLimit);
        int deletedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (RelayStoredObject object : objects) {
            String objectKey = object.objectKey();
            try {
                if (!deletionPolicy.canDelete(objectKey)) {
                    skippedCount++;
                    continue;
                }

                relayObjectStorage.deleteObject(objectKey);
                deletedCount++;
            } catch (RuntimeException e) {
                failedCount++;
                RelayRoomEventLogger.apiWarn("relay_orphan_cleanup_failed", "failed to cleanup relay orphan object",
                    metadata("target", target, "object_key", objectKey, "error", e.getClass().getSimpleName()), e);
            }
        }

        RelayRoomEventLogger.apiBusiness("relay_orphan_cleanup_completed",
            metadata("target", target, "scanned_count", objects.size(), "deleted_count", deletedCount, "skipped_count",
                skippedCount, "failed_count", failedCount, "retention_hours", retention.toHours(), "duration_ms",
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis()));

        return new RelayOrphanObjectCleanupResult(target, objects.size(), deletedCount, skippedCount, failedCount);
    }

    private boolean isTempObjectSafeToDelete(String objectKey) {
        String roomCode = extractRoomCodeFromTempObjectKey(objectKey);
        if (!StringUtils.hasText(roomCode)) {
            return false;
        }

        Optional<RelayRoomState> roomState = relayRoomRepository.findByRoomCode(roomCode);
        if (roomState.isEmpty()) {
            return true;
        }

        RelayRoomStatus status = roomState.get().status();

        return status == RelayRoomStatus.CLOSED || status == RelayRoomStatus.FINISHED;
    }

    private boolean isResultObjectSafeToDelete(String objectKey) {
        if (relayArtifactRepository.existsRelayResultObjectReference(objectKey)) {
            return false;
        }

        return !relayFinalizationAttemptRepository.containsObjectKey(objectKey, scanLimit);
    }

    private String extractRoomCodeFromTempObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey) || !objectKey.startsWith(RELAY_TEMP_OBJECT_KEY_PREFIX)) {
            return null;
        }

        String[] segments = objectKey.split("/");
        if (segments.length < 3) {
            return null;
        }

        return segments[2];
    }

    private interface ObjectDeletionPolicy {

        boolean canDelete(String objectKey);
    }
}
