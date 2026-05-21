package com.nemonicworld.backoffice.metrics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.backoffice.metrics.config.AdminMetricsProperties;
import com.nemonicworld.backoffice.metrics.exception.AdminMetricsException;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

/**
 * Prometheus HTTP API 트랜스포트. {@code /api/v1/query}, {@code /api/v1/query_range}
 * 만 호출한다.
 */
@Component
public class PrometheusClient {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final String QUERY_PATH = "/api/v1/query";
    private static final String QUERY_RANGE_PATH = "/api/v1/query_range";

    private final WebClient webClient;
    private final String baseUrl;
    private final long timeoutMs;

    public PrometheusClient(AdminMetricsProperties properties) {
        this.timeoutMs = properties.resolvedTimeoutMs();
        this.baseUrl = trimTrailingSlash(properties.resolvedPrometheusBaseUrl());
        HttpClient httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
            .responseTimeout(Duration.ofMillis(timeoutMs));
        this.webClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    public JsonNode instantQuery(String promql, long timeEpochSeconds) {
        URI uri = URI.create(baseUrl + QUERY_PATH + "?query=" + urlEncode(promql) + "&time=" + timeEpochSeconds);

        return executeGet(uri);
    }

    public JsonNode rangeQuery(String promql, long startEpochSeconds, long endEpochSeconds, String step) {
        URI uri = URI.create(baseUrl + QUERY_RANGE_PATH + "?query=" + urlEncode(promql) + "&start=" + startEpochSeconds
            + "&end=" + endEpochSeconds + "&step=" + urlEncode(step));

        return executeGet(uri);
    }

    private JsonNode executeGet(URI uri) {
        try {
            JsonNode response = webClient.get().uri(uri).retrieve().bodyToMono(JsonNode.class)
                .block(Duration.ofMillis(timeoutMs));

            return validatePrometheusEnvelope(response);
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    private JsonNode validatePrometheusEnvelope(JsonNode response) {
        if (response == null) {
            throw AdminMetricsException.upstream(null);
        }
        String status = response.path("status").asText("");
        if ("success".equals(status)) {
            return response;
        }

        throw AdminMetricsException.upstream(null);
    }

    private AdminMetricsException translate(RuntimeException error) {
        if (error instanceof AdminMetricsException adminMetricsException) {
            return adminMetricsException;
        }
        if (error instanceof WebClientResponseException) {
            return AdminMetricsException.upstream(error);
        }
        if (isTimeout(error)) {
            return AdminMetricsException.timeout(error);
        }
        if (error instanceof WebClientRequestException) {
            return AdminMetricsException.upstream(error);
        }

        return AdminMetricsException.upstream(error);
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof java.net.SocketTimeoutException
                || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
