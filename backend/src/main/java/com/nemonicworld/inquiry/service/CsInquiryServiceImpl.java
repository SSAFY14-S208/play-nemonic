package com.nemonicworld.inquiry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.inquiry.dto.request.CsInquiryCreateRequest;
import com.nemonicworld.inquiry.dto.response.CsInquiryCreateResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryListItemResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryListResponse;
import com.nemonicworld.inquiry.entity.CsInquiryStatus;
import com.nemonicworld.inquiry.entity.CsInquiryType;
import com.nemonicworld.inquiry.repository.CsInquiryInsertCommand;
import com.nemonicworld.inquiry.repository.CsInquiryRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CsInquiryServiceImpl implements CsInquiryService {

    private static final String INVALID_JSON_MESSAGE = "문의 데이터 형식이 올바르지 않습니다.";
    private static final String REQUIRED_ATTACHMENT_URL_MESSAGE = "첨부 파일 URL을 입력해 주세요.";
    private static final String INVALID_ATTACHMENT_URL_MESSAGE = "첨부 파일 URL 형식이 올바르지 않습니다.";
    private static final String UNAUTHORIZED_MESSAGE = "관리자 인증이 필요합니다.";
    private static final String INVALID_USER_UUID_MESSAGE = "사용자 UUID 형식이 올바르지 않습니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final AnonymousUserResolver anonymousUserResolver;
    private final CsInquiryRepository csInquiryRepository;
    private final ObjectMapper objectMapper;

    public CsInquiryServiceImpl(AnonymousUserResolver anonymousUserResolver, CsInquiryRepository csInquiryRepository,
        ObjectMapper objectMapper) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.csInquiryRepository = csInquiryRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public CsInquiryCreateResponse createInquiry(String userUuid, String userAgent, String referer,
        CsInquiryCreateRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuid);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        String type = CsInquiryType.fromValue(request.type()).getValue();
        CsInquiryInsertCommand command = new CsInquiryInsertCommand(user.getId(), type,
            normalizeRequiredTrimmed(request.title()), normalizeOptional(request.content()),
            normalizeOptional(request.email()), serializeAttachments(request.attachments()),
            serializeMeta(request.meta(), userAgent, referer, now), CsInquiryStatus.NEW.getValue(), now, now);

        return CsInquiryCreateResponse.from(csInquiryRepository.insertInquiry(command));
    }

    @Override
    @Transactional(readOnly = true)
    public CsInquiryListResponse getInquiries(AdminPrincipal adminPrincipal, String status, String type, String keyword,
        String userUuid, String pageValue, String sizeValue) {
        requireAdmin(adminPrincipal);

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

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
        }
    }

    private String normalizeRequiredTrimmed(String value) {
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
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

    private long calculateOffset(int page, int size) {
        return (long) page * size;
    }

    private boolean calculateHasNext(int page, int size, long totalElements) {
        return calculateOffset(page + 1, size) < totalElements;
    }

    private String serializeAttachments(List<String> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }

        validateAttachmentUrls(attachments);

        return writeJson(attachments);
    }

    private void validateAttachmentUrls(List<String> attachments) {
        for (String attachment : attachments) {
            if (!StringUtils.hasText(attachment)) {
                throw new BadRequestException(REQUIRED_ATTACHMENT_URL_MESSAGE);
            }

            if (!isHttpUrl(attachment)) {
                throw new BadRequestException(INVALID_ATTACHMENT_URL_MESSAGE);
            }
        }
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);

            return StringUtils.hasText(uri.getScheme()) && StringUtils.hasText(uri.getHost())
                && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String serializeMeta(Map<String, Object> requestMeta, String userAgent, String referer,
        LocalDateTime createdAt) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (requestMeta != null) {
            meta.putAll(requestMeta);
        }
        if (StringUtils.hasText(userAgent)) {
            meta.put("userAgent", userAgent);
        }
        if (StringUtils.hasText(referer)) {
            meta.put("referer", referer);
        }
        meta.put("createdAt", createdAt.toString());

        return writeJson(meta);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(INVALID_JSON_MESSAGE);
        }
    }
}
