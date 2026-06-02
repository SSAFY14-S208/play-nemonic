package com.nemonicworld.community.service.memo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.community.dto.request.CommunityMemoCreateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoLayoutUpdateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoReportRequest;
import com.nemonicworld.community.entity.CommunityMemoReportReason;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class CommunityMemoValidationSupport {

    private static final String INVALID_REPORT_REASON_MESSAGE = "커뮤니티 메모 신고 사유가 올바르지 않습니다.";
    private static final String INVALID_DECORATION_MESSAGE = "커뮤니티 메모 데코레이션 정보가 올바르지 않습니다.";
    private static final String EMPTY_DECORATION_JSON = "{}";

    private final ObjectMapper objectMapper;

    CommunityMemoValidationSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    CommunityMemoReportReason validateReportReason(CommunityMemoReportRequest request) {
        String reason = request == null ? null : request.reason();
        if (!StringUtils.hasText(reason)) {
            throw new BadRequestException(INVALID_REPORT_REASON_MESSAGE);
        }

        return CommunityMemoReportReason.findByDisplayName(reason)
            .orElseThrow(() -> new BadRequestException(INVALID_REPORT_REASON_MESSAGE));
    }

    String normalizeReasonDetail(String reasonDetail) {
        return StringUtils.hasText(reasonDetail) ? reasonDetail.trim() : null;
    }

    void validatePosition(CommunityMemoCreateRequest request) {
        if (request.positionX() == null || request.positionY() == null || request.zIndex() == null
            || request.rotationDeg() == null || !Double.isFinite(request.positionX())
            || !Double.isFinite(request.positionY()) || !Double.isFinite(request.rotationDeg())
            || Math.abs(request.rotationDeg()) > Float.MAX_VALUE) {
            throw new BadRequestException(CommunityMemoSupport.INVALID_POSITION_MESSAGE);
        }
    }

    void validateLayout(CommunityMemoLayoutUpdateRequest request) {
        if (request == null || request.positionX() == null || request.positionY() == null || request.zIndex() == null
            || request.rotationDeg() == null || !Double.isFinite(request.positionX())
            || !Double.isFinite(request.positionY()) || !Double.isFinite(request.rotationDeg())
            || Math.abs(request.rotationDeg()) > Float.MAX_VALUE) {
            throw new BadRequestException(CommunityMemoSupport.INVALID_POSITION_MESSAGE);
        }
    }

    String serializeDecoration(JsonNode decoration) {
        if (decoration == null || decoration.isNull()) {
            return EMPTY_DECORATION_JSON;
        }

        if (!decoration.isObject()) {
            throw new BadRequestException(INVALID_DECORATION_MESSAGE);
        }

        try {
            return objectMapper.writeValueAsString(decoration);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(INVALID_DECORATION_MESSAGE);
        }
    }
}
