package com.nemonicworld.backoffice.setting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.setting.dto.request.SystemParameterTypedUpdateRequest;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterListResponse;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterResponse;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository.UpdateValueCommand;
import com.nemonicworld.backoffice.setting.service.SystemParameterTypedUpdateMapper.TypedUpdateValue;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.community.service.support.CommunityRuntimeSettingsProvider;
import com.nemonicworld.flipbook.service.support.FlipbookMinFramesPerFlipbookSettings;
import com.nemonicworld.flipbook.service.support.FlipbookReconnectGraceSettings;
import com.nemonicworld.flipbook.service.support.FlipbookRoomParticipantLimit;
import com.nemonicworld.flipbook.service.support.FlipbookRoomTimeLimitSettings;
import com.nemonicworld.flipbook.service.support.FlipbookRuntimeSettingsProvider;
import com.nemonicworld.flipbook.service.support.InvalidFlipbookMinFramesPerFlipbookSettingsException;
import com.nemonicworld.flipbook.service.support.InvalidFlipbookReconnectGraceSettingsException;
import com.nemonicworld.flipbook.service.support.InvalidFlipbookRoomParticipantLimitException;
import com.nemonicworld.flipbook.service.support.InvalidFlipbookRoomTimeLimitSettingsException;
import com.nemonicworld.relay.service.support.InvalidRelayReconnectGraceSettingsException;
import com.nemonicworld.relay.service.support.InvalidRelayRoomParticipantLimitException;
import com.nemonicworld.relay.service.support.InvalidRelayRoomTimeLimitSettingsException;
import com.nemonicworld.relay.service.support.RelayReconnectGraceSettings;
import com.nemonicworld.relay.service.support.RelayRoomParticipantLimit;
import com.nemonicworld.relay.service.support.RelayRoomTimeLimitSettings;
import com.nemonicworld.relay.service.support.RelayRuntimeSettingsProvider;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class SystemParameterServiceImpl implements SystemParameterService {

    private static final Logger log = LoggerFactory.getLogger(SystemParameterServiceImpl.class);

    private static final String UNAUTHORIZED_MESSAGE = "관리자 인증이 필요합니다.";
    private static final String EMPTY_UPDATE_MESSAGE = "수정할 시스템 파라미터를 지정해주세요.";
    private static final String NOT_FOUND_MESSAGE_FORMAT = "존재하지 않는 시스템 파라미터입니다. key=%s";
    private static final String INVALID_VALUE_MESSAGE = "시스템 파라미터 값을 직렬화하지 못했습니다.";
    private static final String INVALID_RELAY_PARTICIPANT_LIMIT_MESSAGE = "릴레이 방 참여 인원 설정이 올바르지 않습니다.";
    private static final String INVALID_RELAY_TIME_LIMIT_MESSAGE = "릴레이 방 그리기 제한 시간 설정이 올바르지 않습니다.";
    private static final String INVALID_RELAY_RECONNECT_GRACE_MESSAGE = "릴레이 재연결 유예 시간 설정이 올바르지 않습니다.";
    private static final String INVALID_FLIPBOOK_PARTICIPANT_LIMIT_MESSAGE = "플립북 방 참여 인원 설정이 올바르지 않습니다.";
    private static final String INVALID_FLIPBOOK_TIME_LIMIT_MESSAGE = "플립북 방 제한 시간 설정이 올바르지 않습니다.";
    private static final String INVALID_FLIPBOOK_MIN_FRAMES_MESSAGE = "플립북 최소 프레임 수 설정이 올바르지 않습니다.";
    private static final String INVALID_FLIPBOOK_RECONNECT_GRACE_MESSAGE = "플립북 재연결 유예 시간 설정이 올바르지 않습니다.";
    private static final String INVALID_SYSTEM_PARAMETER_VALUE_MESSAGE = "시스템 파라미터 값이 올바르지 않습니다.";
    private static final Set<String> POSITIVE_VALUE_SETTING_KEYS = Set.of(
        CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY,
        CommunityRuntimeSettingsProvider.REPORT_HIDE_THRESHOLD_SETTING_KEY, "fortune.daily_limit",
        "cs_inquiry.unresolved_alert_threshold_hours");
    private static final String REDACTED_VALUE = "[redacted]";
    private static final List<String> SENSITIVE_KEY_TOKENS = List.of("password", "secret", "token", "jwt",
        "authorization", "webhook", "smtp", "api_key", "apikey", "access_key", "refresh");

    private final SystemParameterRepository systemParameterRepository;
    private final ObjectMapper objectMapper;
    private final AdminAuditLogger adminAuditLogger;

    public SystemParameterServiceImpl(SystemParameterRepository systemParameterRepository, ObjectMapper objectMapper,
        AdminAuditLogger adminAuditLogger) {
        this.systemParameterRepository = systemParameterRepository;
        this.objectMapper = objectMapper;
        this.adminAuditLogger = adminAuditLogger;
    }

    @Override
    @Transactional(readOnly = true)
    public SystemParameterListResponse getSystemParameters(AdminPrincipal adminPrincipal, String keyword) {
        requireAdmin(adminPrincipal);

        String normalizedKeyword = normalizeKeyword(keyword);
        long totalElements = systemParameterRepository.countAll(normalizedKeyword);
        List<SystemParameterResponse> items = systemParameterRepository.findAll(normalizedKeyword).stream()
            .map(parameter -> SystemParameterResponse.from(parameter, parseValue(parameter.value()))).toList();

        return new SystemParameterListResponse(items, totalElements);
    }

    @Override
    @Transactional
    public SystemParameterListResponse bulkUpdate(AdminPrincipal adminPrincipal,
        SystemParameterTypedUpdateRequest request, AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);

        List<TypedUpdateValue> updates = SystemParameterTypedUpdateMapper.extractUpdates(request, objectMapper);
        if (updates.isEmpty()) {
            throw new BadRequestException(EMPTY_UPDATE_MESSAGE);
        }

        List<String> requestedKeys = updates.stream().map(TypedUpdateValue::key).toList();
        List<SystemParameter> existing = systemParameterRepository.findAllByKeys(requestedKeys);
        Map<String, SystemParameter> existingByKey = new HashMap<>();
        for (SystemParameter parameter : existing) {
            existingByKey.put(parameter.key(), parameter);
        }

        Set<String> missingKeys = new LinkedHashSet<>(requestedKeys);
        missingKeys.removeAll(existingByKey.keySet());
        if (!missingKeys.isEmpty()) {
            throw new BadRequestException(NOT_FOUND_MESSAGE_FORMAT.formatted(missingKeys));
        }

        for (TypedUpdateValue update : updates) {
            validateSystemParameterValue(update.key(), update.value());
        }

        List<UpdateValueCommand> commands = updates.stream().map(update -> {
            SystemParameter previous = existingByKey.get(update.key());
            return new UpdateValueCommand(previous.id(), serializeValue(update.value()), adminPrincipal.id());
        }).toList();

        LocalDateTime now = LocalDateTime.now();
        systemParameterRepository.batchUpdateValues(commands, now);

        Map<Long, String> serializedById = new HashMap<>();
        for (UpdateValueCommand command : commands) {
            serializedById.put(command.id(), command.value());
        }
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (TypedUpdateValue update : updates) {
            SystemParameter previous = existingByKey.get(update.key());
            before.put(previous.key(), safeParameterValue(previous.key(), previous.value()));
            after.put(previous.key(), safeParameterValue(previous.key(), serializedById.get(previous.id())));
            log.info("system-parameter updated id={} key={} updatedBy={}", previous.id(), previous.key(),
                adminPrincipal.id());
        }
        emitAfterCommit(() -> adminAuditLogger.logParamChange(adminPrincipal, "bulk:%d".formatted(updates.size()),
            clientInfo, before, after));

        List<Long> requestedIds = updates.stream().map(update -> existingByKey.get(update.key()).id()).toList();
        List<SystemParameterResponse> updatedItems = systemParameterRepository.findAllByIds(requestedIds).stream()
            .map(parameter -> SystemParameterResponse.from(parameter, parseValue(parameter.value()))).toList();

        return new SystemParameterListResponse(updatedItems, updatedItems.size());
    }

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
        }
    }

    private String normalizeKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private JsonNode parseValue(String value) {
        if (!StringUtils.hasText(value)) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }

    private String serializeValue(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(INVALID_VALUE_MESSAGE);
        }
    }

    private void validateSystemParameterValue(String key, JsonNode value) {
        if (RelayRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY.equals(key)) {
            validateRelayParticipantLimit(value);
            return;
        }

        if (RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY.equals(key)) {
            validateRelayRoomTimeLimit(value);
            return;
        }

        if (RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY.equals(key)) {
            validateRelayReconnectGrace(value);
            return;
        }

        if (FlipbookRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY.equals(key)) {
            validateFlipbookParticipantLimit(value);
            return;
        }

        if (FlipbookRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY.equals(key)) {
            validateFlipbookRoomTimeLimit(value);
            return;
        }

        if (FlipbookRuntimeSettingsProvider.MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY.equals(key)) {
            validateFlipbookMinFramesPerFlipbook(value);
            return;
        }

        if (FlipbookRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY.equals(key)) {
            validateFlipbookReconnectGrace(value);
            return;
        }

        if (POSITIVE_VALUE_SETTING_KEYS.contains(key)) {
            validatePositiveValue(value);
        }
    }

    private void validateRelayParticipantLimit(JsonNode value) {
        try {
            RelayRoomParticipantLimit.fromJson(value);
        } catch (InvalidRelayRoomParticipantLimitException e) {
            throw new BadRequestException(INVALID_RELAY_PARTICIPANT_LIMIT_MESSAGE);
        }
    }

    private void validateRelayRoomTimeLimit(JsonNode value) {
        try {
            RelayRoomTimeLimitSettings.fromJson(value);
        } catch (InvalidRelayRoomTimeLimitSettingsException e) {
            throw new BadRequestException(INVALID_RELAY_TIME_LIMIT_MESSAGE);
        }
    }

    private void validateRelayReconnectGrace(JsonNode value) {
        try {
            RelayReconnectGraceSettings.fromJson(value);
        } catch (InvalidRelayReconnectGraceSettingsException e) {
            throw new BadRequestException(INVALID_RELAY_RECONNECT_GRACE_MESSAGE);
        }
    }

    private void validateFlipbookParticipantLimit(JsonNode value) {
        try {
            FlipbookRoomParticipantLimit.fromJson(value);
        } catch (InvalidFlipbookRoomParticipantLimitException e) {
            throw new BadRequestException(INVALID_FLIPBOOK_PARTICIPANT_LIMIT_MESSAGE);
        }
    }

    private void validateFlipbookRoomTimeLimit(JsonNode value) {
        try {
            FlipbookRoomTimeLimitSettings.fromJson(value);
        } catch (InvalidFlipbookRoomTimeLimitSettingsException e) {
            throw new BadRequestException(INVALID_FLIPBOOK_TIME_LIMIT_MESSAGE);
        }
    }

    private void validateFlipbookMinFramesPerFlipbook(JsonNode value) {
        try {
            FlipbookMinFramesPerFlipbookSettings.fromJson(value);
        } catch (InvalidFlipbookMinFramesPerFlipbookSettingsException e) {
            throw new BadRequestException(INVALID_FLIPBOOK_MIN_FRAMES_MESSAGE);
        }
    }

    private void validateFlipbookReconnectGrace(JsonNode value) {
        try {
            FlipbookReconnectGraceSettings.fromJson(value);
        } catch (InvalidFlipbookReconnectGraceSettingsException e) {
            throw new BadRequestException(INVALID_FLIPBOOK_RECONNECT_GRACE_MESSAGE);
        }
    }

    private void validatePositiveValue(JsonNode value) {
        ensureObject(value);
        requirePositiveIntegerField(value, "value");
    }

    private void ensureObject(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new BadRequestException(INVALID_SYSTEM_PARAMETER_VALUE_MESSAGE);
        }
    }

    private int requirePositiveIntegerField(JsonNode value, String fieldName) {
        return requirePositiveIntegerValue(value.get(fieldName));
    }

    private int requirePositiveIntegerValue(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() <= 0) {
            throw new BadRequestException(INVALID_SYSTEM_PARAMETER_VALUE_MESSAGE);
        }

        return value.asInt();
    }

    private Object safeParameterValue(String key, String value) {
        if (isSensitiveParameterKey(key)) {
            return REDACTED_VALUE;
        }

        return parseValue(value);
    }

    private boolean isSensitiveParameterKey(String key) {
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("-", "_");

        return SENSITIVE_KEY_TOKENS.stream().anyMatch(normalizedKey::contains);
    }

    private void emitAfterCommit(Runnable auditLog) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            auditLog.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auditLog.run();
            }
        });
    }
}
