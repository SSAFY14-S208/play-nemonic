package com.nemonicworld.backoffice.logs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.backoffice.logs.config.AdminLogsProperties;
import com.nemonicworld.backoffice.logs.exception.AdminLogsException;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

@Component
public class OpenSearchClient {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final String NDJSON_MEDIA_TYPE = "application/x-ndjson";

    private final WebClient webClient;
    private final long queryTimeoutMs;

    public OpenSearchClient(AdminLogsProperties properties) {
        this.queryTimeoutMs = properties.resolvedQueryTimeoutMs();
        HttpClient httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
            .responseTimeout(Duration.ofMillis(queryTimeoutMs));
        this.webClient = WebClient.builder().baseUrl(trimTrailingSlash(properties.resolvedOpenSearchUrl()))
            .clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    public JsonNode search(String indexPattern, JsonNode body) {
        try {
            return webClient.post().uri("/" + indexPattern + "/_search").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body).retrieve().bodyToMono(JsonNode.class).block(Duration.ofMillis(queryTimeoutMs));
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    public JsonNode msearch(String body) {
        try {
            return webClient.post().uri("/_msearch").header(HttpHeaders.CONTENT_TYPE, NDJSON_MEDIA_TYPE).bodyValue(body)
                .retrieve().bodyToMono(JsonNode.class).block(Duration.ofMillis(queryTimeoutMs));
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    private AdminLogsException translate(RuntimeException error) {
        if (error instanceof WebClientResponseException responseException) {
            if (responseException.getStatusCode().is4xxClientError()) {
                return AdminLogsException.invalidQuery();
            }
            return AdminLogsException.upstream(responseException);
        }

        if (isTimeout(error)) {
            return AdminLogsException.timeout(error);
        }

        if (error instanceof WebClientRequestException) {
            return AdminLogsException.upstream(error);
        }

        return AdminLogsException.upstream(error);
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

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
