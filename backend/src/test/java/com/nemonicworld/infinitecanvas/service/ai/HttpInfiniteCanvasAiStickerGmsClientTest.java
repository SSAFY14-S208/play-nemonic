package com.nemonicworld.infinitecanvas.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.infinitecanvas.config.InfiniteCanvasAiStickerProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpInfiniteCanvasAiStickerGmsClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void generateUsesGptImageCompatibleRequestBody() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(requestBody);
        HttpInfiniteCanvasAiStickerGmsClient client = new HttpInfiniteCanvasAiStickerGmsClient(
            properties("gpt-image-1", serverUrl()), objectMapper);

        InfiniteCanvasAiStickerImage image = client
            .generate(new InfiniteCanvasAiStickerGmsRequest("system prompt", "토끼", "sticker", 1024, 1024, true));

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(image.bytes()).containsExactly(1, 2, 3);
        assertThat(body.path("model").asText()).isEqualTo("gpt-image-1");
        assertThat(body.path("size").asText()).isEqualTo("1024x1024");
        assertThat(body.has("response_format")).isFalse();
        assertThat(body.path("background").asText()).isEqualTo("transparent");
    }

    private void startServer(AtomicReference<String> requestBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                {
                  "data": [
                    {
                      "b64_json": "%s"
                    }
                  ]
                }
                """.formatted(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3})).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    private String serverUrl() {
        return "http://127.0.0.1:%d/".formatted(server.getAddress().getPort());
    }

    private InfiniteCanvasAiStickerProperties properties(String model, String baseUrl) {
        return new InfiniteCanvasAiStickerProperties(true, 200, 512, 1024, Set.of("sticker"),
            new InfiniteCanvasAiStickerProperties.Gms("test-key", model, baseUrl, 1000L, 5000L));
    }
}
