package com.nemonicworld.community.service.moderation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.community.config.CommunityModerationProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class FastApiCommunityMemoModerationClient implements CommunityMemoModerationClient {

    private static final String MODERATION_RESPONSE_ERROR_MESSAGE = "커뮤니티 메모 모더레이션 응답을 해석할 수 없습니다.";
    private static final String MODERATION_CALL_ERROR_MESSAGE = "커뮤니티 메모 모더레이션 호출에 실패했습니다.";

    private final CommunityModerationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FastApiCommunityMemoModerationClient(CommunityModerationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.resolvedConnectTimeoutMs())).build();
    }

    @Override
    public CommunityMemoModerationResult check(CommunityMemoModerationRequest request) {
        if (!properties.isEnabled()) {
            return CommunityMemoModerationResult.allowedResult();
        }

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(moderationUri())
                .timeout(Duration.ofMillis(properties.resolvedReadTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request))).build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return handleModerationFailure(MODERATION_CALL_ERROR_MESSAGE, null);
            }

            return parseResponse(response.body());
        } catch (JsonProcessingException e) {
            return handleModerationFailure(MODERATION_RESPONSE_ERROR_MESSAGE, e);
        } catch (CommunityMemoModerationException e) {
            return handleModerationFailure(e.getMessage(), e);
        } catch (IOException e) {
            return handleModerationFailure(MODERATION_CALL_ERROR_MESSAGE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return handleModerationFailure(MODERATION_CALL_ERROR_MESSAGE, e);
        } catch (IllegalArgumentException e) {
            return handleModerationFailure(MODERATION_CALL_ERROR_MESSAGE, e);
        }
    }

    private CommunityMemoModerationResult handleModerationFailure(String message, Throwable cause) {
        if (!properties.isFailClosed()) {
            return CommunityMemoModerationResult.allowedResult();
        }

        throw new CommunityMemoModerationException(message, cause);
    }

    private URI moderationUri() {
        String baseUrl = properties.resolvedBaseUrl().replaceAll("/+$", "");
        String checkPath = properties.resolvedCheckPath().replaceAll("^/+", "");

        return URI.create("%s/%s".formatted(baseUrl, checkPath));
    }

    private CommunityMemoModerationResult parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode allowedNode = root.get("allowed");
        if (allowedNode == null || !allowedNode.isBoolean()) {
            throw new CommunityMemoModerationException(MODERATION_RESPONSE_ERROR_MESSAGE);
        }

        JsonNode ocrTextNode = root.get("ocrText");
        JsonNode categoriesNode = root.get("categories");
        String ocrText = ocrTextNode == null || ocrTextNode.isNull() ? null : ocrTextNode.asText();
        JsonNode categories = categoriesNode == null || categoriesNode.isNull() ? null : categoriesNode;

        return new CommunityMemoModerationResult(allowedNode.asBoolean(), ocrText, categories);
    }
}
