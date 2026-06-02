package com.nemonicworld.inquiry.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CsInquiryJsonSupport {

    private static final String INVALID_JSON_MESSAGE = "문의 데이터 형식이 올바르지 않습니다.";
    private static final String REQUIRED_ATTACHMENT_URL_MESSAGE = "첨부 파일 URL을 입력해 주세요.";
    private static final String INVALID_ATTACHMENT_URL_MESSAGE = "첨부 파일 URL 형식이 올바르지 않습니다.";
    private static final TypeReference<List<String>> ATTACHMENTS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> META_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public CsInquiryJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serializeAttachments(List<String> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }

        validateAttachmentUrls(attachments);

        return writeJson(attachments);
    }

    public String serializeMeta(Map<String, Object> requestMeta, String userAgent, String referer,
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

    public List<String> parseAttachments(String attachments) {
        if (!StringUtils.hasText(attachments)) {
            return List.of();
        }

        try {
            List<String> parsedAttachments = objectMapper.readValue(attachments, ATTACHMENTS_TYPE);

            return parsedAttachments == null ? List.of() : parsedAttachments;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public Map<String, Object> parseMeta(String meta) {
        if (!StringUtils.hasText(meta)) {
            return Map.of();
        }

        try {
            Map<String, Object> parsedMeta = objectMapper.readValue(meta, META_TYPE);

            return parsedMeta == null ? Map.of() : parsedMeta;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(INVALID_JSON_MESSAGE);
        }
    }
}
