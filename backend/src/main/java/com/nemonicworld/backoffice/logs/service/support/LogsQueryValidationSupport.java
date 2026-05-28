package com.nemonicworld.backoffice.logs.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.backoffice.logs.config.AdminLogsProperties;
import com.nemonicworld.backoffice.logs.dto.request.LogsFilteredMetricGroup;
import com.nemonicworld.backoffice.logs.dto.request.LogsMetricSpec;
import com.nemonicworld.backoffice.logs.dto.request.LogsSubFilter;
import com.nemonicworld.backoffice.logs.exception.AdminLogsException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LogsQueryValidationSupport {

    private static final int DEFAULT_FIELD_SUMMARY_SIZE = 10;
    private static final int DEFAULT_SEARCH_SIZE = 50;
    private static final int MAX_FIELD_SUMMARY_SIZE = 100;
    private static final int DEFAULT_TERMS_WITH_SUBS_SIZE = 10;
    private static final int MAX_TERMS_WITH_SUBS_SIZE = 50;
    private static final int MAX_TERMS_WITH_SUBS_SUBFILTERS = 5;
    private static final int DEFAULT_TERMS_WITH_METRIC_SIZE = 10;
    private static final int MAX_TERMS_WITH_METRIC_SIZE = 100;
    private static final int MAX_TERMS_WITH_METRIC_METRICS = 3;
    private static final int DEFAULT_COMPOSITE_SIZE = 100;
    private static final int MAX_COMPOSITE_SIZE = 500;
    private static final int MAX_COMPOSITE_SUB_AGGS = 3;
    private static final int COMPOSITE_SOURCES_REQUIRED = 2;
    private static final int MAX_FILTERED_METRICS_GROUPS = 10;
    private static final int DEFAULT_CARDINALITY_PRECISION = 3_000;
    private static final int MAX_CARDINALITY_PRECISION = 40_000;

    private static final Pattern DANGEROUS_SCRIPT_PATTERN = Pattern.compile("(?i)\\bscript(?:\\b|_)");
    private static final Pattern SUB_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,32}");
    private static final Set<String> FORBIDDEN_DSL_KEYS = Set.of("script", "runtime_mappings", "script_fields",
        "_source_includes", "_source_excludes");
    private static final Set<String> FIELD_SUMMARY_FIELDS = Set.of("service", "event_name", "level", "content_type",
        "log_type", "room_id", "trace_id", "error.type", "metadata.funnel_name", "metadata.step_name",
        "metadata.entry_type", "metadata.referrer", "path", "prev_path");
    private static final Set<String> HISTOGRAM_GROUPBY_FIELDS = Set.of("level", "metadata.funnel_name",
        "metadata.entry_type", "event_name", "service");
    private static final Set<String> TERMS_GROUPBY_FIELDS = Set.of("metadata.funnel_name", "metadata.entry_type",
        "event_name", "path", "service");
    private static final Set<String> METRIC_NUMERIC_FIELDS = Set.of("metadata.time_on_page_ms",
        "metadata.time_visible_ms", "metadata.time_in_session_ms", "metadata.elapsed_ms", "metadata.wait_time_ms",
        "metadata.time_on_result_ms");
    private static final Set<String> COMPOSITE_SOURCE_FIELDS = Set.of("metadata.funnel_name", "metadata.step_name",
        "metadata.entry_type", "path", "prev_path", "event_name");
    private static final Set<String> COMPOSITE_SUB_AGG_FIELDS;
    private static final Set<String> CARDINALITY_FIELDS = Set.of("uuid", "session_id", "trace_id");
    private static final Set<String> METRIC_TYPES = Set.of("avg", "sum", "max", "min", "cardinality");
    private static final String METRIC_TYPE_CARDINALITY = "cardinality";
    private static final Pattern FILTER_FIELD_PATTERN = Pattern.compile("[A-Za-z0-9_@.]+");

    static {
        Set<String> compositeSubAggFields = new LinkedHashSet<>(METRIC_NUMERIC_FIELDS);
        compositeSubAggFields.add("metadata.step_index");
        COMPOSITE_SUB_AGG_FIELDS = Set.copyOf(compositeSubAggFields);
    }

    private final AdminLogsProperties properties;

    public LogsQueryValidationSupport(AdminLogsProperties properties) {
        this.properties = properties;
    }

    public void validateRawRequest(JsonNode requestNode) {
        if (requestNode == null || !requestNode.isObject()) {
            throw AdminLogsException.invalidQuery();
        }

        validateNoForbiddenDslKeys(requestNode);
    }

    public int validateSearchSize(Integer requestedSize) {
        int size = requestedSize == null ? DEFAULT_SEARCH_SIZE : requestedSize;
        if (size < 1 || size > properties.resolvedMaxSize()) {
            throw AdminLogsException.invalidQuery();
        }

        return size;
    }

    public int validateFieldSummarySize(Integer requestedSize) {
        int size = requestedSize == null ? DEFAULT_FIELD_SUMMARY_SIZE : requestedSize;
        if (size < 1 || size > MAX_FIELD_SUMMARY_SIZE) {
            throw AdminLogsException.invalidQuery();
        }

        return size;
    }

    public int validateTermsWithSubsSize(Integer requestedSize) {
        return validateRange(requestedSize, DEFAULT_TERMS_WITH_SUBS_SIZE, MAX_TERMS_WITH_SUBS_SIZE);
    }

    public int validateTermsWithMetricSize(Integer requestedSize) {
        return validateRange(requestedSize, DEFAULT_TERMS_WITH_METRIC_SIZE, MAX_TERMS_WITH_METRIC_SIZE);
    }

    public int validateCompositeSize(Integer requestedSize) {
        return validateRange(requestedSize, DEFAULT_COMPOSITE_SIZE, MAX_COMPOSITE_SIZE);
    }

    public int validateCardinalityPrecision(Integer requested) {
        int value = requested == null ? DEFAULT_CARDINALITY_PRECISION : requested;
        if (value < 1 || value > MAX_CARDINALITY_PRECISION) {
            throw AdminLogsException.invalidQuery();
        }

        return value;
    }

    public List<String> validateFieldSummaryFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            throw AdminLogsException.invalidField();
        }

        LinkedHashSet<String> validatedFields = new LinkedHashSet<>();
        for (String field : fields) {
            if (!StringUtils.hasText(field) || !FIELD_SUMMARY_FIELDS.contains(field.trim())) {
                throw AdminLogsException.invalidField();
            }
            validatedFields.add(field.trim());
        }

        return List.copyOf(validatedFields);
    }

    public String validateHistogramGroupBy(String groupBy) {
        if (groupBy == null) {
            return null;
        }

        String trimmed = groupBy.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!HISTOGRAM_GROUPBY_FIELDS.contains(trimmed)) {
            throw AdminLogsException.invalidField();
        }

        return trimmed;
    }

    public String validateTermsGroupBy(String groupBy) {
        if (!StringUtils.hasText(groupBy) || !TERMS_GROUPBY_FIELDS.contains(groupBy.trim())) {
            throw AdminLogsException.invalidField();
        }

        return groupBy.trim();
    }

    public String validateCardinalityField(String field) {
        if (!StringUtils.hasText(field) || !CARDINALITY_FIELDS.contains(field.trim())) {
            throw AdminLogsException.invalidField();
        }

        return field.trim();
    }

    public List<String> validateCompositeSources(List<String> sources) {
        if (sources == null || sources.size() != COMPOSITE_SOURCES_REQUIRED) {
            throw AdminLogsException.invalidQuery();
        }
        for (String source : sources) {
            if (!StringUtils.hasText(source) || !COMPOSITE_SOURCE_FIELDS.contains(source.trim())) {
                throw AdminLogsException.invalidField();
            }
        }

        return sources.stream().map(String::trim).toList();
    }

    public List<LogsSubFilter> validateSubFilters(List<LogsSubFilter> subFilters) {
        if (subFilters == null || subFilters.isEmpty() || subFilters.size() > MAX_TERMS_WITH_SUBS_SUBFILTERS) {
            throw AdminLogsException.invalidQuery();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (LogsSubFilter sub : subFilters) {
            if (sub == null || !StringUtils.hasText(sub.name()) || !SUB_NAME_PATTERN.matcher(sub.name()).matches()) {
                throw AdminLogsException.invalidQuery();
            }
            if (!names.add(sub.name())) {
                throw AdminLogsException.invalidQuery();
            }
            validateNamedQueryString(sub.query());
        }

        return List.copyOf(subFilters);
    }

    public List<LogsMetricSpec> validateTermsWithMetricMetrics(List<LogsMetricSpec> metrics) {
        return validateMetrics(metrics, MAX_TERMS_WITH_METRIC_METRICS, METRIC_NUMERIC_FIELDS);
    }

    public List<LogsMetricSpec> validateCompositeSubAggs(List<LogsMetricSpec> metrics) {
        return validateMetrics(metrics, MAX_COMPOSITE_SUB_AGGS, COMPOSITE_SUB_AGG_FIELDS);
    }

    public List<LogsFilteredMetricGroup> validateFilteredMetricGroups(List<LogsFilteredMetricGroup> groups) {
        if (groups == null || groups.isEmpty() || groups.size() > MAX_FILTERED_METRICS_GROUPS) {
            throw AdminLogsException.invalidQuery();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (LogsFilteredMetricGroup group : groups) {
            if (group == null || !StringUtils.hasText(group.name())
                || !SUB_NAME_PATTERN.matcher(group.name()).matches()) {
                throw AdminLogsException.invalidQuery();
            }
            if (!names.add(group.name())) {
                throw AdminLogsException.invalidQuery();
            }
            validateNamedQueryString(group.query());
            LogsMetricSpec metric = group.metric();
            if (metric != null) {
                String type = StringUtils.hasText(metric.type()) ? metric.type().toLowerCase(Locale.ROOT) : "";
                if (type.isEmpty() || !METRIC_TYPES.contains(type)) {
                    throw AdminLogsException.invalidQuery();
                }
                Set<String> allowedFields = METRIC_TYPE_CARDINALITY.equals(type)
                    ? CARDINALITY_FIELDS
                    : METRIC_NUMERIC_FIELDS;
                if (!StringUtils.hasText(metric.field()) || !allowedFields.contains(metric.field().trim())) {
                    throw AdminLogsException.invalidField();
                }
            }
        }

        return List.copyOf(groups);
    }

    public void validateNamedQueryString(String query) {
        if (!StringUtils.hasText(query)) {
            throw AdminLogsException.invalidQuery();
        }
        validateQueryString(query.trim());
    }

    public void validateQueryString(String query) {
        if (DANGEROUS_SCRIPT_PATTERN.matcher(query).find() || query.contains("\\")) {
            throw AdminLogsException.invalidQuery();
        }
    }

    public String validateFilterFieldName(String field) {
        if (!FILTER_FIELD_PATTERN.matcher(field).matches()) {
            throw AdminLogsException.invalidField();
        }

        return field;
    }

    private void validateNoForbiddenDslKeys(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (FORBIDDEN_DSL_KEYS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    throw AdminLogsException.invalidQuery();
                }
                validateNoForbiddenDslKeys(entry.getValue());
            });
            return;
        }

        if (node.isArray()) {
            node.forEach(this::validateNoForbiddenDslKeys);
        }
    }

    private int validateRange(Integer requestedSize, int defaultSize, int maxSize) {
        int size = requestedSize == null ? defaultSize : requestedSize;
        if (size < 1 || size > maxSize) {
            throw AdminLogsException.invalidQuery();
        }

        return size;
    }

    private List<LogsMetricSpec> validateMetrics(List<LogsMetricSpec> metrics, int maxCount,
        Set<String> allowedNumericFields) {
        if (metrics == null || metrics.isEmpty() || metrics.size() > maxCount) {
            throw AdminLogsException.invalidQuery();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (LogsMetricSpec metric : metrics) {
            if (metric == null || !StringUtils.hasText(metric.name())
                || !SUB_NAME_PATTERN.matcher(metric.name()).matches()) {
                throw AdminLogsException.invalidQuery();
            }
            if (!names.add(metric.name())) {
                throw AdminLogsException.invalidQuery();
            }
            String type = StringUtils.hasText(metric.type()) ? metric.type().toLowerCase(Locale.ROOT) : "";
            if (type.isEmpty() || !METRIC_TYPES.contains(type)) {
                throw AdminLogsException.invalidQuery();
            }
            Set<String> allowedFields = METRIC_TYPE_CARDINALITY.equals(type)
                ? CARDINALITY_FIELDS
                : allowedNumericFields;
            if (!StringUtils.hasText(metric.field()) || !allowedFields.contains(metric.field().trim())) {
                throw AdminLogsException.invalidField();
            }
        }

        return List.copyOf(metrics);
    }
}
