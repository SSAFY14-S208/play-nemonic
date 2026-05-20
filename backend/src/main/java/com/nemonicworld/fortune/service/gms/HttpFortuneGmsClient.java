package com.nemonicworld.fortune.service.gms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nemonicworld.fortune.config.FortuneGmsProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * GMS의 OpenAI 호환 chat completions API로 오늘의 운세 결과를 생성합니다.
 */
@Component
public class HttpFortuneGmsClient implements FortuneGmsClient {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String GMS_CONFIGURATION_ERROR_MESSAGE = "GMS API 설정이 올바르지 않습니다.";
    private static final String GMS_CALL_ERROR_MESSAGE = "GMS API 호출에 실패했습니다.";
    private static final String GMS_RESPONSE_ERROR_MESSAGE = "GMS API 응답을 해석할 수 없습니다.";
    private static final String RESPONSE_FORMAT_INSTRUCTION = """
        너는 오늘의 운세를 생성하는 한국어 운세 해석가다.
        반드시 아래 JSON 객체만 반환한다. 설명 문장, 마크다운 코드블록, 주석은 절대 포함하지 않는다.
        {
          "title": "오늘은 흐름을 정리하는 날",
          "summary": "감정적으로 밀어붙이기보다 차분하게 정리하고 우선순위를 세울수록 좋은 결과가 나는 하루입니다.",
          "overallLuck": 78,
          "loveLuck": 66,
          "workLuck": 84,
          "moneyLuck": 71,
          "luckyColor": "은회색",
          "luckyKeyword": "정리",
          "luckyDirection": "동쪽",
          "caution": "마음이 먼저 앞서면 흐름이 꼬일 수 있으니, 결정은 한 템포 늦추는 것이 좋습니다.",
          "postitLine": "오늘은 정리할수록 운이 열린다",
          "cardTheme": "moon",
          "bgColor": "#F1F3F5",
          "accentColor": "#74808A",
          "iconKey": "moon_waning"
        }
        점수는 0 이상 100 이하의 정수로 만든다.
        cardTheme, bgColor, accentColor, iconKey는 카드 에셋 메타데이터가 없으면 null로 둘 수 있다.
        bgColor와 accentColor를 넣는다면 반드시 #RRGGBB 형식으로 만든다.
        luckyDirection은 동쪽, 서쪽, 남쪽, 북쪽 중 하나로 만든다.
        caution은 공백 포함 32자 이내의 짧은 문장으로 만든다.
        postitLine은 네모닉 출력에 어울리는 짧은 한 문장으로 만든다.
        """;

    private final FortuneGmsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpFortuneGmsClient(FortuneGmsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.resolvedConnectTimeoutMs())).build();
    }

    @Override
    public FortuneGmsResult generate(String promptTemplate, JsonNode saju) {
        validateProperties();

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.resolvedBaseUrl()))
                .timeout(Duration.ofMillis(properties.resolvedReadTimeoutMs()))
                .header("Content-Type", CONTENT_TYPE_JSON).header("Authorization", BEARER_PREFIX + properties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(createRequestBody(promptTemplate, saju))).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FortuneGmsException(GMS_CALL_ERROR_MESSAGE);
            }

            return parseResponse(response.body());
        } catch (JsonProcessingException e) {
            throw new FortuneGmsException(GMS_RESPONSE_ERROR_MESSAGE, e);
        } catch (IOException e) {
            throw new FortuneGmsException(GMS_CALL_ERROR_MESSAGE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FortuneGmsException(GMS_CALL_ERROR_MESSAGE, e);
        } catch (IllegalArgumentException e) {
            throw new FortuneGmsException(GMS_CONFIGURATION_ERROR_MESSAGE, e);
        }
    }

    private void validateProperties() {
        if (!properties.hasApiKey() || !StringUtils.hasText(properties.resolvedBaseUrl())) {
            throw new FortuneGmsException(GMS_CONFIGURATION_ERROR_MESSAGE);
        }
    }

    private String createRequestBody(String promptTemplate, JsonNode saju) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.resolvedModel());
        ArrayNode messages = root.putArray("messages");
        messages.add(
            message("developer", "%s%n%n%s".formatted(String.valueOf(promptTemplate), RESPONSE_FORMAT_INSTRUCTION)));
        messages.add(message("user", createUserMessage(saju)));

        return objectMapper.writeValueAsString(root);
    }

    private ObjectNode message(String role, String content) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);

        return message;
    }

    private String createUserMessage(JsonNode saju) throws JsonProcessingException {
        return "다음 만세력 계산 결과를 바탕으로 오늘의 운세를 생성해줘.%n%s"
            .formatted(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(saju));
    }

    private FortuneGmsResult parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (!contentNode.isTextual() || !StringUtils.hasText(contentNode.asText())) {
            throw new FortuneGmsException(GMS_RESPONSE_ERROR_MESSAGE);
        }

        JsonNode result = objectMapper.readTree(stripJsonFence(contentNode.asText()));

        return new FortuneGmsResult(requiredText(result, "title"), requiredText(result, "summary"),
            requiredScore(result, "overallLuck"), requiredScore(result, "loveLuck"), requiredScore(result, "workLuck"),
            requiredScore(result, "moneyLuck"), requiredText(result, "luckyColor"),
            requiredText(result, "luckyKeyword"), requiredText(result, "luckyDirection"),
            optionalText(result, "caution"), requiredText(result, "postitLine"), optionalText(result, "cardTheme"),
            optionalText(result, "bgColor"), optionalText(result, "accentColor"), optionalText(result, "iconKey"));
    }

    private String stripJsonFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        String withoutOpeningFence = trimmed.replaceFirst("^```(?:json)?\\s*", "");

        return withoutOpeningFence.replaceFirst("\\s*```$", "").trim();
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        if (!StringUtils.hasText(value)) {
            throw new FortuneGmsException(GMS_RESPONSE_ERROR_MESSAGE);
        }

        return value;
    }

    private String optionalText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);

        return value.isMissingNode() || value.isNull() ? null : value.asText().trim();
    }

    private int requiredScore(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (!value.canConvertToInt()) {
            throw new FortuneGmsException(GMS_RESPONSE_ERROR_MESSAGE);
        }

        return value.asInt();
    }
}
