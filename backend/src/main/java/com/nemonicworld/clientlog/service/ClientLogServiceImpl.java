package com.nemonicworld.clientlog.service;

import com.nemonicworld.clientlog.config.ClientLogProperties;
import com.nemonicworld.clientlog.dto.request.ClientLogIngestRequest;
import com.nemonicworld.clientlog.dto.response.ClientLogIngestResponse;
import com.nemonicworld.common.exception.TooManyRequestsException;
import com.nemonicworld.global.logging.StructuredEventLogger;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientLogServiceImpl implements ClientLogService {

    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "클라이언트 로그 전송 한도를 초과했습니다.";
    private static final Set<String> REQUIRED_FIELDS = Set.of("@timestamp", "event_name", "service");
    private static final Set<String> ALLOWED_EVENT_NAMES = Set.copyOf(List.of(
        // Spec §3: acquisition
        "landing_source_detected", "campaign_attributed", "share_link_opened", "install_prompt_shown",
        "install_prompt_accepted", "install_prompt_dismissed",
        // Spec §4: session/page
        "session_start", "session_end", "page_view", "page_leave", "visibility_change", "client_alive",
        // Spec §5: funnel
        "funnel_started", "funnel_step_viewed", "funnel_step_completed", "funnel_goal_reached", "funnel_abandoned",
        "cta_clicked",
        // Spec §6: abandonment
        "page_exit_intent_detected", "room_lobby_abandoned", "creation_abandoned", "result_share_abandoned",
        // Spec §7: UI engagement
        "scroll_depth_reached", "modal_opened", "modal_closed", "tool_selected", "canvas_interaction_started",
        "canvas_interaction_paused",
        // Spec §8: performance/error
        "web_vitals", "resource_load_slow", "js_error", "unhandled_rejection", "client_network_failed"));
    private static final Set<String> ERROR_EVENT_NAMES = Set.of("js_error", "unhandled_rejection",
        "client_network_failed");
    private static final List<String> STANDARD_FIELD_NAMES = List.of("flow_id", "session_id", "room_id", "prev_zone",
        "prev_path", "path", "referrer");
    private static final Pattern BOT_USER_AGENT_PATTERN = Pattern
        .compile("(?i).*(bot|crawler|spider|preview|facebookexternalhit|slackbot|discordbot).*");
    private static final Pattern EVENT_NAME_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";

    private final ClientLogProperties properties;
    private final ClientLogRateLimiter rateLimiter;
    private final ClientLogSanitizer sanitizer;

    public ClientLogServiceImpl(ClientLogProperties properties, ClientLogRateLimiter rateLimiter,
        ClientLogSanitizer sanitizer) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.sanitizer = sanitizer;
    }

    @Override
    public ClientLogIngestResponse ingest(ClientLogIngestRequest request, HttpServletRequest servletRequest) {
        List<Map<String, Object>> events = request.events();
        String ipAddress = resolveIpAddress(servletRequest);
        if (isBot(servletRequest)) {
            logDropSummary("bot_user_agent", events.size(), ipAddress);
            return new ClientLogIngestResponse(0, events.size(), 0);
        }

        if (!isAllowedOrigin(servletRequest)) {
            logDropSummary("disallowed_origin", events.size(), ipAddress);
            return new ClientLogIngestResponse(0, events.size(), 0);
        }

        if (!rateLimiter.tryConsume(ipAddress, events.size())) {
            throw new TooManyRequestsException(RATE_LIMIT_EXCEEDED_MESSAGE);
        }

        int acceptedCount = 0;
        int droppedCount = 0;
        int systemRoutedCount = 0;
        Map<String, Integer> dropReasons = new LinkedHashMap<>();
        Map<String, Integer> unlistedEventNames = new LinkedHashMap<>();

        for (Map<String, Object> event : events) {
            String validationFailure = validate(event);
            if (validationFailure != null) {
                droppedCount++;
                dropReasons.merge(validationFailure, 1, Integer::sum);
                continue;
            }

            String eventName = text(event.get("event_name"));
            if (!ALLOWED_EVENT_NAMES.contains(eventName)) {
                systemRoutedCount++;
                unlistedEventNames.merge(eventName, 1, Integer::sum);
                continue;
            }

            emitClientEvent(event, eventName);
            acceptedCount++;
        }

        logDropReasons(dropReasons);
        logUnlistedEvents(unlistedEventNames);

        return new ClientLogIngestResponse(acceptedCount, droppedCount, systemRoutedCount);
    }

    private String validate(Map<String, Object> event) {
        for (String requiredField : REQUIRED_FIELDS) {
            if (!StringUtils.hasText(text(event.get(requiredField)))) {
                return "missing_" + requiredField.replace("@", "");
            }
        }

        String eventName = text(event.get("event_name"));
        if (!EVENT_NAME_PATTERN.matcher(eventName).matches()) {
            return "invalid_event_name";
        }

        return null;
    }

    private void emitClientEvent(Map<String, Object> event, String eventName) {
        Map<String, Object> metadata = sanitizedMap(event.get("metadata"));
        metadata.put("client_timestamp", sanitizer.sanitize(event.get("@timestamp")));

        StructuredEventLogger.clientEvent(ERROR_EVENT_NAMES.contains(eventName) ? "ERROR" : "INFO",
            text(event.get("service")), eventName, sanitizedText(event.get("message")), text(event.get("trace_id")),
            sanitizedText(event.get("content_type")), sanitizedText(event.get("uuid")), standardFields(event), metadata,
            sanitizedMap(event.get("error")));
    }

    private Map<String, Object> standardFields(Map<String, Object> event) {
        Map<String, Object> standardFields = new LinkedHashMap<>();
        for (String fieldName : STANDARD_FIELD_NAMES) {
            Object value = event.get(fieldName);
            if (value != null) {
                standardFields.put(fieldName, sanitizer.sanitize(value));
            }
        }

        return standardFields;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizedMap(Object value) {
        if (!(value instanceof Map<?, ?> mapValue)) {
            return new LinkedHashMap<>();
        }

        return (Map<String, Object>) sanitizer.sanitize(mapValue);
    }

    private void logDropReasons(Map<String, Integer> dropReasons) {
        for (Map.Entry<String, Integer> entry : dropReasons.entrySet()) {
            logDropSummary(entry.getKey(), entry.getValue(), null);
        }
    }

    private void logDropSummary(String reason, int count, String ipAddress) {
        StructuredEventLogger.clientWarn("client_log_events_dropped", "client log events dropped",
            StructuredEventLogger.metadata("reason", reason, "count", count, "ip_address", ipAddress));
    }

    private void logUnlistedEvents(Map<String, Integer> unlistedEventNames) {
        for (Map.Entry<String, Integer> entry : unlistedEventNames.entrySet()) {
            StructuredEventLogger.clientWarn("client_log_event_unlisted", "client log event is not allow-listed",
                StructuredEventLogger.metadata("event_name", entry.getKey(), "count", entry.getValue(), "reason",
                    "unlisted_event_name"));
        }
    }

    private boolean isBot(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        return StringUtils.hasText(userAgent) && BOT_USER_AGENT_PATTERN.matcher(userAgent).matches();
    }

    private boolean isAllowedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (StringUtils.hasText(origin)) {
            return properties.getAllowedOrigins().contains(normalizeOrigin(origin));
        }

        String referer = request.getHeader(HttpHeaders.REFERER);
        if (StringUtils.hasText(referer)) {
            return properties.getAllowedOrigins().contains(normalizeOrigin(referer));
        }

        return false;
    }

    private String normalizeOrigin(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return "";
            }

            StringBuilder origin = new StringBuilder(uri.getScheme().toLowerCase(Locale.ROOT)).append("://")
                .append(uri.getHost().toLowerCase(Locale.ROOT));
            if (uri.getPort() >= 0) {
                origin.append(":").append(uri.getPort());
            }

            return origin.toString();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader(X_REAL_IP);
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }

        return StringUtils.hasText(request.getRemoteAddr()) ? request.getRemoteAddr() : UNKNOWN;
    }

    private String sanitizedText(Object value) {
        if (value == null) {
            return null;
        }

        return sanitizer.sanitizeString(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
