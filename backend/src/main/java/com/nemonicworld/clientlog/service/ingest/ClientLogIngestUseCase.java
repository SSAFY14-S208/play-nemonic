package com.nemonicworld.clientlog.service.ingest;

import com.nemonicworld.clientlog.dto.request.ClientLogIngestRequest;
import com.nemonicworld.clientlog.dto.response.ClientLogIngestResponse;
import com.nemonicworld.clientlog.service.ClientLogRateLimiter;
import com.nemonicworld.clientlog.service.support.ClientLogEventEmitter;
import com.nemonicworld.clientlog.service.support.ClientLogEventValidator;
import com.nemonicworld.clientlog.service.support.ClientLogRequestGuard;
import com.nemonicworld.common.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ClientLogIngestUseCase {

    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "클라이언트 로그 전송 한도를 초과했습니다.";
    private static final Set<String> ALLOWED_EVENT_NAMES = Set.copyOf(List.of(
        // Spec 3: acquisition
        "landing_source_detected", "campaign_attributed", "share_link_opened", "install_prompt_shown",
        "install_prompt_accepted", "install_prompt_dismissed",
        // Spec 4: session/page
        "session_start", "session_end", "page_view", "page_leave", "visibility_change", "client_alive",
        // Spec 5: funnel
        "funnel_started", "funnel_step_viewed", "funnel_step_completed", "funnel_goal_reached", "funnel_abandoned",
        "cta_clicked", "result_shared",
        // Spec 6: abandonment
        "page_exit_intent_detected", "room_lobby_abandoned", "creation_abandoned", "result_share_abandoned",
        // Spec 7: UI engagement
        "scroll_depth_reached", "modal_opened", "modal_closed", "tool_selected", "canvas_interaction_started",
        "canvas_interaction_paused", "phone_official_store_clicked",
        // Spec 8: performance/error
        "web_vitals", "resource_load_slow", "js_error", "unhandled_rejection", "client_network_failed"));

    private final ClientLogRateLimiter rateLimiter;
    private final ClientLogRequestGuard requestGuard;
    private final ClientLogEventValidator eventValidator;
    private final ClientLogEventEmitter eventEmitter;

    public ClientLogIngestUseCase(ClientLogRateLimiter rateLimiter, ClientLogRequestGuard requestGuard,
        ClientLogEventValidator eventValidator, ClientLogEventEmitter eventEmitter) {
        this.rateLimiter = rateLimiter;
        this.requestGuard = requestGuard;
        this.eventValidator = eventValidator;
        this.eventEmitter = eventEmitter;
    }

    public ClientLogIngestResponse ingest(ClientLogIngestRequest request, HttpServletRequest servletRequest) {
        List<Map<String, Object>> events = request.events();
        String ipAddress = requestGuard.resolveIpAddress(servletRequest);
        if (requestGuard.isBot(servletRequest)) {
            eventEmitter.logDropSummary("bot_user_agent", events.size(), ipAddress);
            return new ClientLogIngestResponse(0, events.size(), 0);
        }

        if (!requestGuard.isAllowedOrigin(servletRequest)) {
            eventEmitter.logDropSummary("disallowed_origin", events.size(), ipAddress);
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
            String validationFailure = eventValidator.validate(event);
            if (validationFailure != null) {
                droppedCount++;
                dropReasons.merge(validationFailure, 1, Integer::sum);
                continue;
            }

            String eventName = eventValidator.text(event.get("event_name"));
            if (!ALLOWED_EVENT_NAMES.contains(eventName)) {
                systemRoutedCount++;
                unlistedEventNames.merge(eventName, 1, Integer::sum);
                continue;
            }

            eventEmitter.emitClientEvent(event, eventName);
            acceptedCount++;
        }

        eventEmitter.logDropReasons(dropReasons);
        eventEmitter.logUnlistedEvents(unlistedEventNames);

        return new ClientLogIngestResponse(acceptedCount, droppedCount, systemRoutedCount);
    }
}
