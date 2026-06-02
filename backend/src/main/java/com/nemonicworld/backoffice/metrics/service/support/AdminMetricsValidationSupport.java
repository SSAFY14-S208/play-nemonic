package com.nemonicworld.backoffice.metrics.service.support;

import com.nemonicworld.backoffice.metrics.dto.request.MetricsTimeRange;
import com.nemonicworld.backoffice.metrics.exception.AdminMetricsException;
import com.nemonicworld.backoffice.metrics.service.MetricsTemplateRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

@Component
public class AdminMetricsValidationSupport {

    private static final Duration MAX_TIME_RANGE = Duration.ofDays(30);

    public ParsedTimeRange parseTimeRange(MetricsTimeRange timeRange) {
        if (timeRange == null || timeRange.from() == null || timeRange.to() == null) {
            throw AdminMetricsException.invalidTimeRange();
        }
        Instant from;
        Instant to;
        try {
            from = Instant.parse(timeRange.from());
            to = Instant.parse(timeRange.to());
        } catch (DateTimeParseException e) {
            throw AdminMetricsException.invalidTimeRange();
        }
        if (!from.isBefore(to)) {
            throw AdminMetricsException.invalidTimeRange();
        }
        if (Duration.between(from, to).compareTo(MAX_TIME_RANGE) > 0) {
            throw AdminMetricsException.invalidTimeRange();
        }

        return new ParsedTimeRange(from.getEpochSecond(), to.getEpochSecond());
    }

    public String validateStep(String step) {
        if (step == null || !MetricsTemplateRegistry.isValidStep(step)) {
            throw AdminMetricsException.invalidParam();
        }

        return step;
    }

    public record ParsedTimeRange(long fromSeconds, long toSeconds) {
    }
}
