package com.nemonicworld.backoffice.logs.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nemonicworld.backoffice.logs.dto.request.LogsMetricSpec;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class LogsAggregationBuilder {

    public static final String COMPOSITE_MISSING_BUCKET = "__missing__";

    private static final int HISTOGRAM_GROUPBY_SIZE = 20;
    private static final int DEFAULT_CARDINALITY_PRECISION = 3_000;
    private static final String METRIC_TYPE_CARDINALITY = "cardinality";

    private final ObjectMapper objectMapper;
    private final LogsQueryValidationSupport validationSupport;

    public LogsAggregationBuilder(ObjectMapper objectMapper, LogsQueryValidationSupport validationSupport) {
        this.objectMapper = objectMapper;
        this.validationSupport = validationSupport;
    }

    public ObjectNode buildHistogramAggregations(String interval, Instant from, Instant to, String groupBy) {
        ObjectNode aggs = objectMapper.createObjectNode();
        ObjectNode ts = aggs.putObject("ts");
        ObjectNode dateHistogram = ts.putObject("date_histogram");
        dateHistogram.put("field", "@timestamp");
        dateHistogram.put("fixed_interval", interval);
        dateHistogram.put("min_doc_count", 0);
        ObjectNode bounds = dateHistogram.putObject("extended_bounds");
        bounds.put("min", from.toString());
        bounds.put("max", to.toString());
        ObjectNode subAggs = ts.putObject("aggs");
        ObjectNode level = subAggs.putObject("level").putObject("terms");
        level.put("field", "level");
        level.put("size", 10);
        level.put("missing", "UNKNOWN");

        if (groupBy != null) {
            ObjectNode byField = subAggs.putObject("by_field").putObject("terms");
            byField.put("field", groupBy);
            byField.put("size", HISTOGRAM_GROUPBY_SIZE);
            byField.put("missing", "_missing");
        }

        return aggs;
    }

    public ObjectNode buildTermsAggregation(String field, int size) {
        ObjectNode aggs = objectMapper.createObjectNode();
        ObjectNode terms = aggs.putObject("values").putObject("terms");
        terms.put("field", field);
        terms.put("size", size);

        return aggs;
    }

    public ObjectNode buildFilterAgg(String rawQuery) {
        validationSupport.validateNamedQueryString(rawQuery);
        ObjectNode wrapper = objectMapper.createObjectNode();
        ObjectNode filter = wrapper.putObject("filter");
        ObjectNode queryString = filter.putObject("query_string");
        queryString.put("query", rawQuery.trim());

        return wrapper;
    }

    public ObjectNode buildMetricAgg(LogsMetricSpec metric) {
        String type = metric.type().toLowerCase(Locale.ROOT);
        ObjectNode wrapper = objectMapper.createObjectNode();
        ObjectNode agg = wrapper.putObject(type);
        agg.put("field", metric.field().trim());
        if (METRIC_TYPE_CARDINALITY.equals(type)) {
            agg.put("precision_threshold", DEFAULT_CARDINALITY_PRECISION);
        }

        return wrapper;
    }
}
