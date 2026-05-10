package com.nemonicworld.backoffice.flipbook.service;

import com.nemonicworld.backoffice.flipbook.dto.response.BackofficeFlipbookRoomListResponse;
import com.nemonicworld.backoffice.flipbook.dto.response.BackofficeFlipbookRoomResponse;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BackofficeFlipbookRoomServiceImpl implements BackofficeFlipbookRoomService {

    private static final String UNAUTHORIZED_MESSAGE = "관리자 인증이 필요합니다.";
    private static final String INVALID_STATUS_MESSAGE = "조회할 수 없는 플립북 방 상태입니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final FlipbookRoomRepository flipbookRoomRepository;

    public BackofficeFlipbookRoomServiceImpl(FlipbookRoomRepository flipbookRoomRepository) {
        this.flipbookRoomRepository = flipbookRoomRepository;
    }

    @Override
    public BackofficeFlipbookRoomListResponse getActiveFlipbookRooms(AdminPrincipal adminPrincipal, String status,
        String page, String size) {
        requireAdmin(adminPrincipal);

        FlipbookRoomStatus statusFilter = parseStatusFilter(status);
        int pageNumber = parsePage(page);
        int pageSize = parseSize(size);

        List<FlipbookRoomState> activeRooms = flipbookRoomRepository.findAllActiveRooms();
        List<FlipbookRoomState> filtered = activeRooms.stream()
            .filter(room -> room.status() != FlipbookRoomStatus.CLOSED)
            .filter(room -> statusFilter == null || room.status() == statusFilter)
            .sorted(Comparator.comparing(FlipbookRoomState::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FlipbookRoomState::roomCode, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        long totalElements = filtered.size();
        int fromIndex = Math.min(pageNumber * pageSize, filtered.size());
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        List<BackofficeFlipbookRoomResponse> items = filtered.subList(fromIndex, toIndex).stream()
            .map(BackofficeFlipbookRoomResponse::from).toList();

        return new BackofficeFlipbookRoomListResponse(items, totalElements, pageNumber, pageSize);
    }

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
        }
    }

    private FlipbookRoomStatus parseStatusFilter(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        FlipbookRoomStatus parsed;
        try {
            parsed = FlipbookRoomStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_STATUS_MESSAGE);
        }

        if (parsed == FlipbookRoomStatus.CLOSED) {
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
