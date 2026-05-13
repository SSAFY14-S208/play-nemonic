package com.nemonicworld.backoffice.relay.service;

import com.nemonicworld.backoffice.relay.dto.response.BackofficeRelayRoomDeleteResponse;
import com.nemonicworld.backoffice.relay.dto.response.BackofficeRelayRoomListResponse;
import com.nemonicworld.backoffice.relay.dto.response.BackofficeRelayRoomResponse;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.close.RelayRoomCloseCommand;
import com.nemonicworld.relay.service.close.RelayRoomCloseResult;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

@Service
public class BackofficeRelayRoomServiceImpl implements BackofficeRelayRoomService {

    private static final String UNAUTHORIZED_MESSAGE = "관리자 인증이 필요합니다.";
    private static final String INVALID_STATUS_MESSAGE = "조회할 수 없는 방 상태입니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";
    private static final String ROOM_ALREADY_CLOSED_MESSAGE = "이미 종료된 방입니다.";

    private static final String IN_PROGRESS_STATUS_FILTER = "IN_PROGRESS";
    private static final Set<RelayRoomStatus> DEFAULT_ACTIVE_STATUS_FILTER = Set.of(RelayRoomStatus.WAITING,
        RelayRoomStatus.PLAYING, RelayRoomStatus.FINALIZING);

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomCloseCommand relayRoomCloseCommand;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final AdminAuditLogger adminAuditLogger;

    public BackofficeRelayRoomServiceImpl(RelayRoomRepository relayRoomRepository, RelayRoomPolicy relayRoomPolicy,
        RelayRoomCloseCommand relayRoomCloseCommand, RelayRoomEventPublisher relayRoomEventPublisher,
        AdminAuditLogger adminAuditLogger) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomCloseCommand = relayRoomCloseCommand;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.adminAuditLogger = adminAuditLogger;
    }

    @Override
    public BackofficeRelayRoomListResponse getActiveRelayRooms(AdminPrincipal adminPrincipal, String status,
        String page, String size) {
        requireAdmin(adminPrincipal);

        Set<RelayRoomStatus> statusFilter = parseStatusFilter(status);
        int pageNumber = parsePage(page);
        int pageSize = parseSize(size);

        List<RelayRoomState> activeRooms = relayRoomRepository.findAllActiveRooms();
        List<RelayRoomState> filtered = activeRooms.stream().filter(room -> statusFilter.contains(room.status()))
            .sorted(Comparator.comparing(RelayRoomState::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RelayRoomState::roomCode, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        long totalElements = filtered.size();
        int fromIndex = Math.min(pageNumber * pageSize, filtered.size());
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        List<BackofficeRelayRoomResponse> items = filtered.subList(fromIndex, toIndex).stream()
            .map(BackofficeRelayRoomResponse::from).toList();

        return new BackofficeRelayRoomListResponse(items, totalElements, pageNumber, pageSize);
    }

    @Override
    public BackofficeRelayRoomDeleteResponse deleteActiveRelayRoom(AdminPrincipal adminPrincipal, String roomCode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        relayRoomPolicy.validateRoomCode(roomCode);
        LocalDateTime closedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCode);
            if (roomState.status() == RelayRoomStatus.CLOSED) {
                throw new ConflictException(ROOM_ALREADY_CLOSED_MESSAGE);
            }

            RelayRoomCloseResult closeResult = relayRoomCloseCommand.closeActiveRoomIfUnchanged(roomState, closedAt);
            if (closeResult.closed()) {
                RelayRoomEventLogger.apiBusiness("relay_room_closed",
                    metadata("room_id", closeResult.roomCode(), "close_reason", "admin_force", "room_status_before",
                        roomState.status(), "participant_count", roomState.participantCount()));
                relayRoomEventPublisher.publishRoomClosed(closeResult.roomCode(), closeResult.closedAt(),
                    "admin_force");
                adminAuditLogger.logRelayRoomForceClose(adminPrincipal, closeResult.roomCode(),
                    roomState.status().name(), clientInfo);
                return new BackofficeRelayRoomDeleteResponse(closeResult.roomCode());
            }
        }

        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
        }
    }

    private Set<RelayRoomStatus> parseStatusFilter(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_ACTIVE_STATUS_FILTER;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (IN_PROGRESS_STATUS_FILTER.equals(normalized)) {
            return EnumSet.of(RelayRoomStatus.PLAYING, RelayRoomStatus.FINALIZING);
        }

        RelayRoomStatus parsed;
        try {
            parsed = RelayRoomStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_STATUS_MESSAGE);
        }

        if (parsed == RelayRoomStatus.CLOSED) {
            throw new BadRequestException(INVALID_STATUS_MESSAGE);
        }

        return EnumSet.of(parsed);
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
