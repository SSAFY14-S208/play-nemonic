package com.nemonicworld.inquiry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.inquiry.dto.request.CsInquiryCreateRequest;
import com.nemonicworld.inquiry.dto.response.CsInquiryCreateResponse;
import com.nemonicworld.inquiry.entity.CsInquiryStatus;
import com.nemonicworld.inquiry.entity.CsInquiryType;
import com.nemonicworld.inquiry.repository.CsInquiryInsertCommand;
import com.nemonicworld.inquiry.repository.CsInquiryRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CsInquiryServiceImpl implements CsInquiryService {

    private static final String INVALID_JSON_MESSAGE = "Invalid inquiry JSON data.";

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

    private String normalizeRequiredTrimmed(String value) {
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private String serializeAttachments(List<String> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }

        return writeJson(attachments);
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
