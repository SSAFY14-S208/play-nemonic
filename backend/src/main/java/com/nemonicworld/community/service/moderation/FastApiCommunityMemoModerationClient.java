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
            // 로컬 개발이나 장애 대응 시 모더레이션을 끄면 게시 흐름을 그대로 통과시킵니다.
            // 운영 기본값은 enabled=true라서 실제 게시 전 검수 경로를 탑니다.
            return CommunityMemoModerationResult.allowedResult();
        }

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(moderationUri())
                // Uvicorn 개발 서버가 Java HttpClient의 HTTP/2 upgrade 요청을 잘못 해석하지 않도록 고정합니다.
                .version(HttpClient.Version.HTTP_1_1).timeout(Duration.ofMillis(properties.resolvedReadTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request))).build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // FastAPI가 정상 2xx를 돌려주지 못하면 설정된 fail-open/closed 정책으로 통일 처리합니다.
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
            // OCR/API 장애는 신고 정책으로 보완할 수 있으므로 기본 운영은 fail-open으로 둡니다.
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
            // FastAPI 계약이 깨지면 설정에 따라 게시 허용 또는 예외 처리됩니다.
            throw new CommunityMemoModerationException(MODERATION_RESPONSE_ERROR_MESSAGE);
        }

        // ocrText/categories는 저장 가능한 부가 정보라서 누락되어도 검수 성공 여부 판단에는 영향을 주지 않습니다.
        JsonNode ocrTextNode = root.get("ocrText");
        JsonNode categoriesNode = root.get("categories");
        String ocrText = ocrTextNode == null || ocrTextNode.isNull() ? null : ocrTextNode.asText();
        JsonNode categories = categoriesNode == null || categoriesNode.isNull() ? null : categoriesNode;

        return new CommunityMemoModerationResult(allowedNode.asBoolean(), ocrText, categories);
    }
}
