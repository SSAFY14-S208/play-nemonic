package com.nemonicworld.backoffice.relay.service;

import com.nemonicworld.backoffice.relay.dto.response.BackofficeRelayRoomListResponse;
import com.nemonicworld.backoffice.relay.dto.response.BackofficeRelayRoomResponse;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BackofficeRelayRoomServiceImpl implements BackofficeRelayRoomService {

    private static final String UNAUTHORIZED_MESSAGE = "관리자 인증이 필요합니다.";
    private static final String INVALID_STATUS_MESSAGE = "조회할 수 없는 방 상태입니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final RelayRoomRepository relayRoomRepository;

    public BackofficeRelayRoomServiceImpl(RelayRoomRepository relayRoomRepository) {
        this.relayRoomRepository = relayRoomRepository;
    }

    @Override
    public BackofficeRelayRoomListResponse getActiveRelayRooms(AdminPrincipal adminPrincipal, String status,
        String page, String size) {
        requireAdmin(adminPrincipal);

        RelayRoomStatus statusFilter = parseStatusFilter(status);
        int pageNumber = parsePage(page);
        int pageSize = parseSize(size);

        List<RelayRoomState> activeRooms = relayRoomRepository.findAllActiveRooms();
        List<RelayRoomState> filtered = activeRooms.stream()
            .filter(room -> statusFilter == null || room.status() == statusFilter)
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

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
        }
    }

    private RelayRoomStatus parseStatusFilter(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        RelayRoomStatus parsed;
        try {
            parsed = RelayRoomStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_STATUS_MESSAGE);
        }

        if (parsed == RelayRoomStatus.CLOSED) {
            throw new BadRequestException(INVALID_STATUS_MESSAGE);
        }

        return parsed;
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
