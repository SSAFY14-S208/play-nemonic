package com.nemonicworld.infinitecanvas.service.close;

import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasSimpleMessageResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.websocket.InfiniteCanvasEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * connected 참여자가 없고 일정 시간 갱신이 없는 ACTIVE 무한 캔버스를 정리한다.
 *
 * <p>릴레이/플립북의 {@code findEmptyWaitingRooms} + {@code RoomAbandonedCloseService}
 * 패턴을 따른다. 사용자가 leaveCanvas API 호출 없이 브라우저를 닫거나 네트워크
 * 끊김으로 나간 경우 disconnectCanvas는 participant.connected=false만 표시하고
 * 방을 닫지 않으므로, 모든 참여자가 disconnected 상태로 남으면 캔버스가 영구
 * ACTIVE로 남는 문제를 보정한다.
 *
 * <p>grace period(기본 1분)를 두는 이유: 페이지 새로고침·앱 전환 같은 단기 끊김
 * 후 reconnect 시나리오를 보호한다. updatedAt이 cutoff 이전이고 connected가
 * 한 명도 없을 때만 닫기 후보로 잡는다.
 */
@Service
public class InfiniteCanvasAbandonedCloseService {

    private static final Logger log = LoggerFactory.getLogger(InfiniteCanvasAbandonedCloseService.class);

    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasEventPublisher infiniteCanvasEventPublisher;
    private final Duration idleDuration;
    private final int scanLimit;

    public InfiniteCanvasAbandonedCloseService(InfiniteCanvasRepository infiniteCanvasRepository,
        InfiniteCanvasEventPublisher infiniteCanvasEventPublisher,
        @Value("${nemonic.infinite-canvas.abandoned-close.idle-seconds:60}") long idleSeconds,
        @Value("${nemonic.infinite-canvas.abandoned-close.scan-limit:100}") int scanLimit) {
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.infiniteCanvasEventPublisher = infiniteCanvasEventPublisher;
        this.idleDuration = Duration.ofSeconds(Math.max(0L, idleSeconds));
        this.scanLimit = scanLimit;
    }

    public InfiniteCanvasAbandonedCloseProcessResult closeAbandonedCanvases() {
        return closeAbandonedCanvases(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public InfiniteCanvasAbandonedCloseProcessResult closeAbandonedCanvases(LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime idleCutoff = closedAt.minus(idleDuration);
        List<InfiniteCanvasState> candidates = infiniteCanvasRepository.findAbandonedActiveCanvases(idleCutoff,
            scanLimit);

        int closedCount = 0;
        for (InfiniteCanvasState canvasState : candidates) {
            try {
                InfiniteCanvasState closedState = closedCopy(canvasState, closedAt);
                if (infiniteCanvasRepository.saveIfUnchanged(canvasState, closedState)) {
                    infiniteCanvasRepository.delete(canvasState.roomCode());
                    infiniteCanvasEventPublisher.publishCanvasClosed(canvasState.roomCode(),
                        new InfiniteCanvasSimpleMessageResponse("abandoned_empty"));
                    closedCount++;
                    log.info("infinite canvas closed by abandoned scheduler. room_code={} idle_seconds={}",
                        canvasState.roomCode(), idleDuration.getSeconds());
                }
            } catch (RuntimeException e) {
                log.warn("Failed to close abandoned infinite canvas. room_code={}", canvasState.roomCode(), e);
            }
        }

        return new InfiniteCanvasAbandonedCloseProcessResult(candidates.size(), closedCount);
    }

    private InfiniteCanvasState closedCopy(InfiniteCanvasState canvasState, LocalDateTime closedAt) {
        return new InfiniteCanvasState(canvasState.roomCode(), InfiniteCanvasStatus.CLOSED,
            canvasState.hostUserUuid(), canvasState.participants(), canvasState.elements(), canvasState.operations(),
            canvasState.locks(), canvasState.cursors(), canvasState.viewport(), canvasState.maxParticipants(),
            canvasState.revision(), canvasState.createdAt(), closedAt, closedAt);
    }
}
