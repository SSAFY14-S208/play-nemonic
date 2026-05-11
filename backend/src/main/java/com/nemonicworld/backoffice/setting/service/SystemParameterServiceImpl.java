package com.nemonicworld.backoffice.setting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.setting.dto.request.SystemParameterBulkUpdateItem;
import com.nemonicworld.backoffice.setting.dto.request.SystemParameterBulkUpdateRequest;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterListResponse;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterResponse;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository.UpdateValueCommand;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
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
    private static final String DUPLICATE_ID_MESSAGE = "동일한 시스템 파라미터 ID가 중복되었습니다.";
    private static final String NOT_FOUND_MESSAGE_FORMAT = "존재하지 않는 시스템 파라미터입니다. id=%s";
    private static final String INVALID_VALUE_MESSAGE = "시스템 파라미터 값을 직렬화하지 못했습니다.";
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
        SystemParameterBulkUpdateRequest request, AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);

        List<SystemParameterBulkUpdateItem> items = request.items();
        List<Long> requestedIds = items.stream().map(SystemParameterBulkUpdateItem::id).toList();
        Set<Long> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new BadRequestException(DUPLICATE_ID_MESSAGE);
        }

        List<SystemParameter> existing = systemParameterRepository.findAllByIds(requestedIds);
        Map<Long, SystemParameter> existingById = new HashMap<>();
        for (SystemParameter parameter : existing) {
            existingById.put(parameter.id(), parameter);
        }

        Set<Long> missingIds = new LinkedHashSet<>(requestedIds);
        missingIds.removeAll(existingById.keySet());
        if (!missingIds.isEmpty()) {
            throw new BadRequestException(NOT_FOUND_MESSAGE_FORMAT.formatted(missingIds));
        }

        List<UpdateValueCommand> commands = items.stream()
            .map(item -> new UpdateValueCommand(item.id(), serializeValue(item.value()), adminPrincipal.id())).toList();

        LocalDateTime now = LocalDateTime.now();
        systemParameterRepository.batchUpdateValues(commands, now);

        Map<Long, String> serializedById = new HashMap<>();
        for (UpdateValueCommand command : commands) {
            serializedById.put(command.id(), command.value());
        }
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (SystemParameterBulkUpdateItem item : items) {
            SystemParameter previous = existingById.get(item.id());
            before.put(previous.key(), safeParameterValue(previous.key(), previous.value()));
            after.put(previous.key(), safeParameterValue(previous.key(), serializedById.get(item.id())));
            log.info("system-parameter updated id={} key={} updatedBy={}", previous.id(), previous.key(),
                adminPrincipal.id());
        }
        emitAfterCommit(() -> adminAuditLogger.logParamChange(adminPrincipal, "bulk:%d".formatted(items.size()),
            clientInfo, before, after));

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
