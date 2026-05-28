package com.nemonicworld.inquiry.service.admin;

import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.inquiry.dto.response.CsInquiryDetailResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryListItemResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryListResponse;
import com.nemonicworld.inquiry.entity.CsInquiryStatus;
import com.nemonicworld.inquiry.entity.CsInquiryType;
import com.nemonicworld.inquiry.repository.CsInquiryRepository;
import com.nemonicworld.inquiry.service.support.CsInquiryJsonSupport;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminInquiryQueryUseCase {

    private static final String INVALID_USER_UUID_MESSAGE = "사용자 UUID 형식이 올바르지 않습니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";
    private static final String INVALID_INQUIRY_ID_MESSAGE = "문의 ID가 올바르지 않습니다.";
    private static final String INQUIRY_NOT_FOUND_MESSAGE = "고객 문의를 찾을 수 없습니다.";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final CsInquiryRepository csInquiryRepository;
    private final CsInquiryJsonSupport csInquiryJsonSupport;

    public AdminInquiryQueryUseCase(CsInquiryRepository csInquiryRepository,
        CsInquiryJsonSupport csInquiryJsonSupport) {
        this.csInquiryRepository = csInquiryRepository;
        this.csInquiryJsonSupport = csInquiryJsonSupport;
    }

    @Transactional(readOnly = true)
    public CsInquiryListResponse getInquiries(AdminPrincipal adminPrincipal, String status, String type, String keyword,
        String userUuid, String pageValue, String sizeValue) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);

        int page = parsePage(pageValue);
        int size = parseSize(sizeValue);
        String normalizedStatus = normalizeOptionalStatus(status);
        String normalizedType = normalizeOptionalType(type);
        String normalizedKeyword = normalizeOptionalKeyword(keyword);
        UUID normalizedUserUuid = parseOptionalUserUuid(userUuid);

        long totalElements = csInquiryRepository.countInquiries(normalizedStatus, normalizedType, normalizedKeyword,
            normalizedUserUuid);
        List<CsInquiryListItemResponse> items = csInquiryRepository.findInquiries(normalizedStatus, normalizedType,
            normalizedKeyword, normalizedUserUuid, size, calculateOffset(page, size)).stream()
            .map(CsInquiryListItemResponse::from).toList();

        return new CsInquiryListResponse(items, page, size, totalElements, calculateHasNext(page, size, totalElements));
    }

    @Transactional(readOnly = true)
    public CsInquiryDetailResponse getInquiry(AdminPrincipal adminPrincipal, String inquiryIdValue) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);

        Long inquiryId = parseInquiryId(inquiryIdValue);

        return csInquiryRepository.findById(inquiryId)
            .map(inquiry -> CsInquiryDetailResponse.of(inquiry,
                csInquiryJsonSupport.parseAttachments(inquiry.getAttachments()),
                csInquiryJsonSupport.parseMeta(inquiry.getMeta())))
            .orElseThrow(() -> new NotFoundException(INQUIRY_NOT_FOUND_MESSAGE));
    }

    private String normalizeOptionalKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedStatus = value.trim().toLowerCase(Locale.ROOT);

        return CsInquiryStatus.fromValue(normalizedStatus).getValue();
    }

    private String normalizeOptionalType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedType = value.trim().toLowerCase(Locale.ROOT);

        return CsInquiryType.fromValue(normalizedType).getValue();
    }

    private UUID parseOptionalUserUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_USER_UUID_MESSAGE);
        }
    }

    private int parsePage(String pageValue) {
        int page = parseIntegerOrDefault(pageValue, DEFAULT_PAGE);
        if (page < 0) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }

        return page;
    }

    private int parseSize(String sizeValue) {
        int size = parseIntegerOrDefault(sizeValue, DEFAULT_SIZE);
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }

        return size;
    }

    private int parseIntegerOrDefault(String value, int defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }
    }

    private Long parseInquiryId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(INVALID_INQUIRY_ID_MESSAGE);
        }

        try {
            long inquiryId = Long.parseLong(value);
            if (inquiryId <= 0) {
                throw new BadRequestException(INVALID_INQUIRY_ID_MESSAGE);
            }

            return inquiryId;
        } catch (NumberFormatException e) {
            throw new BadRequestException(INVALID_INQUIRY_ID_MESSAGE);
        }
    }

    private long calculateOffset(int page, int size) {
        return (long) page * size;
    }

    private boolean calculateHasNext(int page, int size, long totalElements) {
        return calculateOffset(page + 1, size) < totalElements;
    }
}
