package com.nemonicworld.backoffice.infinitecanvas.service.room;

import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasListResponse;
import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasResponse;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasActiveCanvasPage;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BackofficeInfiniteCanvasQueryUseCase {

    private static final String INVALID_STATUS_MESSAGE = "조회할 수 없는 무한 캔버스 상태입니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";

    private static final Set<InfiniteCanvasStatus> DEFAULT_ACTIVE_STATUS_FILTER = Set.of(InfiniteCanvasStatus.ACTIVE);

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final InfiniteCanvasRepository infiniteCanvasRepository;

    public BackofficeInfiniteCanvasQueryUseCase(InfiniteCanvasRepository infiniteCanvasRepository) {
        this.infiniteCanvasRepository = infiniteCanvasRepository;
    }

    public BackofficeInfiniteCanvasListResponse getActiveCanvases(AdminPrincipal adminPrincipal, String status,
        String page, String size) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);

        Set<InfiniteCanvasStatus> statusFilter = parseStatusFilter(status);
        int pageNumber = parsePage(page);
        int pageSize = parseSize(size);

        InfiniteCanvasActiveCanvasPage activeCanvasPage = infiniteCanvasRepository.findActiveCanvases(pageNumber,
            pageSize);
        if (!statusFilter.contains(InfiniteCanvasStatus.ACTIVE)) {
            return new BackofficeInfiniteCanvasListResponse(List.of(), 0L, pageNumber, pageSize);
        }

        return new BackofficeInfiniteCanvasListResponse(
            activeCanvasPage.items().stream().map(BackofficeInfiniteCanvasResponse::from).toList(),
            activeCanvasPage.totalElements(), pageNumber, pageSize);
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
