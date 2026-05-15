package com.nemonicworld.backoffice.infinitecanvas.service;

import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasCloseResponse;
import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasListResponse;
import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasResponse;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.websocket.InfiniteCanvasEventPublisher;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BackofficeInfiniteCanvasServiceImpl implements BackofficeInfiniteCanvasService {

    private static final String UNAUTHORIZED_MESSAGE = "관리자 인증이 필요합니다.";
    private static final String INVALID_STATUS_MESSAGE = "조회할 수 없는 무한 캔버스 상태입니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";
    private static final String INVALID_CANVAS_ID_MESSAGE = "유효하지 않은 캔버스 ID 형식입니다.";
    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";
    private static final String CANVAS_ALREADY_CLOSED_MESSAGE = "이미 종료된 캔버스입니다.";
    private static final String CANVAS_UPDATE_CONFLICT_MESSAGE = "무한 캔버스 상태 갱신 충돌이 발생했습니다. 다시 시도해주세요.";

    private static final Set<InfiniteCanvasStatus> DEFAULT_ACTIVE_STATUS_FILTER = Set.of(InfiniteCanvasStatus.ACTIVE);

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int CANVAS_UPDATE_MAX_RETRIES = 8;

    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasEventPublisher infiniteCanvasEventPublisher;
    private final AdminAuditLogger adminAuditLogger;

    public BackofficeInfiniteCanvasServiceImpl(InfiniteCanvasRepository infiniteCanvasRepository,
        InfiniteCanvasEventPublisher infiniteCanvasEventPublisher, AdminAuditLogger adminAuditLogger) {
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.infiniteCanvasEventPublisher = infiniteCanvasEventPublisher;
        this.adminAuditLogger = adminAuditLogger;
    }

    @Override
    public BackofficeInfiniteCanvasListResponse getActiveCanvases(AdminPrincipal adminPrincipal, String status,
        String page, String size) {
        requireAdmin(adminPrincipal);

        Set<InfiniteCanvasStatus> statusFilter = parseStatusFilter(status);
        int pageNumber = parsePage(page);
        int pageSize = parseSize(size);

        List<InfiniteCanvasState> filtered = infiniteCanvasRepository.findAllActiveCanvases().stream()
            .filter(canvas -> canvas.status() != InfiniteCanvasStatus.CLOSED)
            .filter(canvas -> statusFilter.contains(canvas.status()))
            .sorted(
                Comparator.comparing(InfiniteCanvasState::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(InfiniteCanvasState::canvasId, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        long totalElements = filtered.size();
        int fromIndex = Math.min(pageNumber * pageSize, filtered.size());
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        List<BackofficeInfiniteCanvasResponse> items = filtered.subList(fromIndex, toIndex).stream()
            .map(BackofficeInfiniteCanvasResponse::from).toList();

        return new BackofficeInfiniteCanvasListResponse(items, totalElements, pageNumber, pageSize);
    }

    @Override
    public BackofficeInfiniteCanvasCloseResponse closeActiveCanvas(AdminPrincipal adminPrincipal, String canvasId,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        String normalizedCanvasId = normalizeCanvasId(canvasId);
        LocalDateTime closedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        for (int attempt = 0; attempt < CANVAS_UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = infiniteCanvasRepository.findByCanvasId(normalizedCanvasId)
                .orElseThrow(() -> new NotFoundException(CANVAS_NOT_FOUND_MESSAGE));
            if (state.status() == InfiniteCanvasStatus.CLOSED) {
                throw new ConflictException(CANVAS_ALREADY_CLOSED_MESSAGE);
            }

            InfiniteCanvasState closedState = closeState(state, closedAt);
            if (infiniteCanvasRepository.saveIfUnchanged(state, closedState)) {
                StructuredEventLogger.apiBusiness("infinite_canvas_closed", "infinite_canvas", state.ownerUserUuid(),
                    StructuredEventLogger.metadata("canvas_id", closedState.canvasId(), "close_reason", "admin_force",
                        "canvas_status_before", state.status(), "participant_count", state.participantCount(),
                        "connected_participant_count", state.connectedParticipantCount()));
                infiniteCanvasEventPublisher.publishCanvasClosed(closedState.canvasId(), closedAt);
                adminAuditLogger.logInfiniteCanvasForceClose(adminPrincipal, closedState.canvasId(),
                    state.status().name(), clientInfo);
                return new BackofficeInfiniteCanvasCloseResponse(closedState.canvasId());
            }
        }

        throw new ConflictException(CANVAS_UPDATE_CONFLICT_MESSAGE);
    }

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
        }
    }

    private Set<InfiniteCanvasStatus> parseStatusFilter(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_ACTIVE_STATUS_FILTER;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        InfiniteCanvasStatus parsed;
        try {
            parsed = InfiniteCanvasStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_STATUS_MESSAGE);
        }

        if (parsed == InfiniteCanvasStatus.CLOSED) {
            throw new BadRequestException(INVALID_STATUS_MESSAGE);
        }

        return EnumSet.of(parsed);
    }

    private String normalizeCanvasId(String canvasId) {
        if (!StringUtils.hasText(canvasId)) {
            throw new BadRequestException(INVALID_CANVAS_ID_MESSAGE);
        }

        try {
            return UUID.fromString(canvasId.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_CANVAS_ID_MESSAGE);
        }
    }

    private InfiniteCanvasState closeState(InfiniteCanvasState state, LocalDateTime closedAt) {
        return new InfiniteCanvasState(state.canvasId(), state.inviteCode(), InfiniteCanvasStatus.CLOSED,
            state.ownerUserUuid(), state.participants(), state.elements(), state.operations(), Map.of(), Map.of(),
            state.viewport(), state.maxParticipants(), state.revision(), state.createdAt(), closedAt, closedAt);
    }

    private int parsePage(String value) {
        int parsed = parseIntegerOrDefault(value, DEFAULT_PAGE);
        if (parsed < 0) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }

        return parsed;
    }

    private int parseSize(String value) {
        int parsed = parseIntegerOrDefault(value, DEFAULT_SIZE);
        if (parsed < 1) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }

        return Math.min(parsed, MAX_SIZE);
    }

    private int parseIntegerOrDefault(String value, int defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }
    }
}
