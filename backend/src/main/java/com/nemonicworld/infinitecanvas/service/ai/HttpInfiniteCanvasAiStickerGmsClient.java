package com.nemonicworld.infinitecanvas.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.infinitecanvas.config.InfiniteCanvasAiStickerProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class HttpInfiniteCanvasAiStickerGmsClient implements InfiniteCanvasAiStickerGmsClient {

    private static final Logger log = LoggerFactory.getLogger(HttpInfiniteCanvasAiStickerGmsClient.class);

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String GMS_CONFIGURATION_ERROR_MESSAGE = "AI 스티커 GMS API 설정이 올바르지 않습니다.";
    private static final String GMS_CALL_ERROR_MESSAGE = "AI 스티커 GMS API 호출에 실패했습니다.";
    private static final String GMS_RESPONSE_ERROR_MESSAGE = "AI 스티커 GMS API 응답을 해석할 수 없습니다.";

    private final InfiniteCanvasAiStickerProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpInfiniteCanvasAiStickerGmsClient(InfiniteCanvasAiStickerProperties properties,
        ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.resolvedGms().resolvedConnectTimeoutMs())).build();
    }

    @Override
    public InfiniteCanvasAiStickerImage generate(InfiniteCanvasAiStickerGmsRequest request) {
        validateProperties();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.resolvedGms().resolvedBaseUrl()))
                .timeout(Duration.ofMillis(properties.resolvedGms().resolvedReadTimeoutMs()))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .header("Authorization", BEARER_PREFIX + properties.resolvedGms().apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(createRequestBody(request))).build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI sticker GMS request failed. statusCode={} responseBodyHash={}", response.statusCode(),
                    StructuredEventLogger.sha256Prefix(response.body()));
                throw new InfiniteCanvasAiStickerException(
                    "%s status=%d".formatted(GMS_CALL_ERROR_MESSAGE, response.statusCode()));
            }

            return parseResponse(response.body());
        } catch (JsonProcessingException e) {
            throw new InfiniteCanvasAiStickerException(GMS_RESPONSE_ERROR_MESSAGE, e);
        } catch (IOException e) {
            throw new InfiniteCanvasAiStickerException(GMS_CALL_ERROR_MESSAGE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfiniteCanvasAiStickerException(GMS_CALL_ERROR_MESSAGE, e);
        } catch (IllegalArgumentException e) {
            throw new InfiniteCanvasAiStickerException(GMS_CONFIGURATION_ERROR_MESSAGE, e);
        }
    }

    private void validateProperties() {
        if (!properties.resolvedGms().hasApiKey() || !StringUtils.hasText(properties.resolvedGms().resolvedBaseUrl())) {
            throw new InfiniteCanvasAiStickerException(GMS_CONFIGURATION_ERROR_MESSAGE);
        }
    }

    private String createRequestBody(InfiniteCanvasAiStickerGmsRequest request) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.resolvedGms().resolvedModel());
        root.put("prompt", createPrompt(request));
        root.put("n", 1);
        root.put("size", "%dx%d".formatted(request.width(), request.height()));
        if (!isGptImageModel()) {
            root.put("response_format", "b64_json");
        }
        if (request.transparentBackground() && isGptImageModel()) {
            root.put("background", "transparent");
        }

        return objectMapper.writeValueAsString(root);
    }

    private boolean isGptImageModel() {
        return properties.resolvedGms().resolvedModel().toLowerCase(Locale.ROOT).startsWith("gpt-image");
    }

    private String createPrompt(InfiniteCanvasAiStickerGmsRequest request) {
        return """
            %s

            사용자 요청: %s
            스타일: %s
            출력 조건:
            - 단일 스티커 이미지
            - 투명 배경 PNG
            - 캔버스 위에서 작게 배치해도 알아보기 쉬운 명확한 실루엣
            - 텍스트, 워터마크, 프레임 없음
            """.formatted(request.systemPrompt(), request.userPrompt(), request.style());
    }

    private InfiniteCanvasAiStickerImage parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode firstData = root.path("data").path(0);
        String b64Json = firstText(firstData.path("b64_json"), firstData.path("image_base64"),
            firstData.path("base64"));
        if (!StringUtils.hasText(b64Json)) {
            throw new InfiniteCanvasAiStickerException(GMS_RESPONSE_ERROR_MESSAGE);
        }

        try {
            return new InfiniteCanvasAiStickerImage(Base64.getDecoder().decode(stripDataUrlPrefix(b64Json)),
                PNG_CONTENT_TYPE);
        } catch (IllegalArgumentException e) {
            throw new InfiniteCanvasAiStickerException(GMS_RESPONSE_ERROR_MESSAGE, e);
        }
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
                return node.asText().trim();
            }
        }

        return null;
    }

    private String stripDataUrlPrefix(String value) {
        int commaIndex = value.indexOf(',');
        return value.startsWith("data:") && commaIndex >= 0 ? value.substring(commaIndex + 1) : value;
    }
}
