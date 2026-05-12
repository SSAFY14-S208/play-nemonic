package com.nemonicworld.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.global.config.OpenApiConfig;
import com.nemonicworld.support.IntegrationTest;
import io.swagger.v3.oas.models.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
/**
 * Swagger/OpenAPI 문서에 컴파일러가 만든 임시 파라미터명이 노출되지 않는지 검증합니다.
 */
class OpenApiParameterNamingIntegrationTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Swagger UI의 카테고리가 한글 이름과 지정한 업무 흐름 순서로 노출되는지 확인합니다.
     */
    @Test
    void openApiTagsUseKoreanNamesInDisplayOrder() throws Exception {
        JsonNode tags = getOpenApiRoot().path("tags");
        List<String> actualTagNames = new ArrayList<>();
        for (JsonNode tag : tags) {
            actualTagNames.add(tag.path("name").asText());
        }

        List<String> expectedTagNames = OpenApiTags.orderedTags().stream().map(Tag::getName).toList();

        assertEquals(expectedTagNames, actualTagNames, "OpenAPI tags must use the configured Korean display order.");
    }

    /**
     * Swagger UI 탭별 문서가 업무 영역에 맞는 API만 포함하는지 확인합니다.
     */
    @Test
    void openApiGroupsExposeRelatedApiPaths() throws Exception {
        assertGroupContainsPaths("all", List.of("/api/v1/relay/rooms", "/api/v1/gallery", "/api/logs/client"),
            List.of());
        assertGroupContainsPaths("common",
            List.of("/api/v1/users/anonymous", "/api/v1/auth/login", "/api/v1/files/presign"),
            List.of("/api/v1/relay/rooms", "/api/v1/backoffice/system-parameters"));
        assertGroupContainsPaths("contents",
            List.of("/api/v1/gallery", "/api/v1/community/memos", "/api/v1/fortune/today"),
            List.of("/api/v1/relay/rooms", "/api/v1/admins"));
        assertGroupContainsPaths("games", List.of("/api/v1/relay/rooms", "/api/v1/flipbook/rooms"),
            List.of("/api/v1/files/presign", "/api/v1/gallery"));
        assertGroupContainsPaths("support-logs", List.of("/api/v1/inquiries", "/api/logs/client"),
            List.of("/api/v1/admin/inquiries", "/api/v1/relay/rooms"));
        assertGroupContainsPaths("backoffice",
            List.of("/api/v1/admins", "/api/v1/admin/community/memos", "/api/v1/backoffice/system-parameters"),
            List.of("/api/v1/inquiries", "/api/v1/relay/rooms"));
    }

    /**
     * 컨트롤러 파라미터 이름이 명시되지 않으면 Swagger에 arg0, arg1 같은 이름이 노출될 수 있습니다.
     */
    @Test
    void openApiParametersDoNotUseCompilerGeneratedArgumentNames() throws Exception {
        JsonNode paths = getOpenApiPaths();
        List<String> compilerGeneratedParameterNames = new ArrayList<>();

        for (Map.Entry<String, JsonNode> pathEntry : paths.properties()) {
            collectCompilerGeneratedParameterNames(pathEntry.getKey(), pathEntry.getValue(),
                compilerGeneratedParameterNames);
        }

        assertTrue(compilerGeneratedParameterNames.isEmpty(),
            () -> "OpenAPI parameter names must be explicit: " + compilerGeneratedParameterNames);
    }

    /**
     * Swagger에서 실패 응답이 Undocumented로 표시되지 않도록 각 operation에 실패 응답 코드와 예시를 문서화합니다.
     */
    @Test
    void openApiOperationsExposeFailureResponses() throws Exception {
        JsonNode paths = getOpenApiPaths();
        List<String> operationsWithoutFailureResponses = new ArrayList<>();
        List<String> failureResponsesWithoutExamples = new ArrayList<>();

        for (Map.Entry<String, JsonNode> pathEntry : paths.properties()) {
            collectOperationsWithoutFailureResponses(pathEntry.getKey(), pathEntry.getValue(),
                operationsWithoutFailureResponses);
            collectFailureResponsesWithoutExamples(pathEntry.getKey(), pathEntry.getValue(),
                failureResponsesWithoutExamples);
        }

        assertTrue(operationsWithoutFailureResponses.isEmpty(),
            () -> "OpenAPI operations must document at least one failure response: "
                + operationsWithoutFailureResponses);
        assertTrue(failureResponsesWithoutExamples.isEmpty(),
            () -> "OpenAPI failure responses must include examples with success=false: "
                + failureResponsesWithoutExamples);
    }

    /**
     * 공통 응답의 errors 필드가 additionalProp 자동 예시 대신 필드 오류 용도로 설명되는지 확인합니다.
     */
    @Test
    void openApiCommonResponseDocumentsErrorsField() throws Exception {
        JsonNode components = getOpenApiComponents();
        List<String> apiResponseSchemasWithoutErrorsDescription = new ArrayList<>();
        List<String> apiResponseSchemasWithDomainSpecificErrorsExample = new ArrayList<>();

        for (Map.Entry<String, JsonNode> schemaEntry : components.path("schemas").properties()) {
            String schemaName = schemaEntry.getKey();
            if (!schemaName.startsWith("ApiResponse")) {
                continue;
            }

            JsonNode errors = schemaEntry.getValue().path("properties").path("errors");
            if (!errors.path("description").asText("").contains("필드별 오류 상세 정보")) {
                apiResponseSchemasWithoutErrorsDescription.add(schemaName);
            }
            if (errors.path("example").toString().contains("fileName")
                || errors.path("example").toString().contains("contentType")) {
                apiResponseSchemasWithDomainSpecificErrorsExample.add(schemaName);
            }
        }

        assertTrue(apiResponseSchemasWithoutErrorsDescription.isEmpty(),
            () -> "ApiResponse errors field must include a clear description: "
                + apiResponseSchemasWithoutErrorsDescription);
        assertTrue(apiResponseSchemasWithDomainSpecificErrorsExample.isEmpty(),
            () -> "ApiResponse errors field must not use domain-specific example keys: "
                + apiResponseSchemasWithDomainSpecificErrorsExample);
    }

    @Test
    void openApiDocumentsAdminBearerAuthentication() throws Exception {
        JsonNode root = getOpenApiRoot();
        JsonNode scheme = root.path("components").path("securitySchemes").path(OpenApiConfig.BEARER_AUTH_SCHEME);

        assertTrue("http".equals(scheme.path("type").asText()), "Admin auth must use an HTTP security scheme.");
        assertTrue("bearer".equals(scheme.path("scheme").asText()), "Admin auth must use bearer tokens.");
        assertTrue("JWT".equals(scheme.path("bearerFormat").asText()), "Admin auth must document JWT format.");
        assertTrue(hasSecurityRequirement(root, "/api/v1/admins", "post"),
            "Admin account creation must require bearer auth in Swagger.");
        assertTrue(hasSecurityRequirement(root, "/api/v1/admins", "get"),
            "Admin account listing must require bearer auth in Swagger.");
        assertTrue(hasSecurityRequirement(root, "/api/v1/admins/{adminId}", "get"),
            "Admin account detail lookup must require bearer auth in Swagger.");
        assertTrue(hasSecurityRequirement(root, "/api/v1/admins/{adminId}", "patch"),
            "Admin password change must require bearer auth in Swagger.");
        assertTrue(hasSecurityRequirement(root, "/api/v1/admins/{adminId}", "delete"),
            "Admin account deletion must require bearer auth in Swagger.");
        assertTrue(hasSecurityRequirement(root, "/api/v1/auth/logout", "post"),
            "Admin logout must require bearer auth in Swagger.");
    }

    private JsonNode getOpenApiPaths() throws Exception {
        return getOpenApiRoot().path("paths");
    }

    private JsonNode getOpenApiComponents() throws Exception {
        return getOpenApiRoot().path("components");
    }

    private JsonNode getOpenApiRoot() throws Exception {
        return getOpenApiRoot("/v3/api-docs");
    }

    private JsonNode getOpenApiRoot(String docsPath) throws Exception {
        byte[] responseBody = mockMvc.perform(get(docsPath)).andExpect(status().isOk()).andReturn().getResponse()
            .getContentAsByteArray();
        String body = new String(responseBody, StandardCharsets.UTF_8);

        return objectMapper.readTree(body);
    }

    private void assertGroupContainsPaths(String group, List<String> expectedPaths, List<String> unexpectedPaths)
        throws Exception {
        JsonNode paths = getOpenApiRoot("/v3/api-docs/" + group).path("paths");

        for (String expectedPath : expectedPaths) {
            assertTrue(paths.has(expectedPath), () -> group + " OpenAPI group must include " + expectedPath);
        }
        for (String unexpectedPath : unexpectedPaths) {
            assertTrue(!paths.has(unexpectedPath), () -> group + " OpenAPI group must not include " + unexpectedPath);
        }
    }

    private boolean hasSecurityRequirement(JsonNode root, String path, String method) {
        JsonNode securityRequirements = root.path("paths").path(path).path(method).path("security");
        for (JsonNode securityRequirement : securityRequirements) {
            if (securityRequirement.has(OpenApiConfig.BEARER_AUTH_SCHEME)) {
                return true;
            }
        }

        return false;
    }

    private void collectCompilerGeneratedParameterNames(String path, JsonNode pathItem,
        List<String> compilerGeneratedParameterNames) {
        for (Map.Entry<String, JsonNode> operationEntry : pathItem.properties()) {
            if (!HTTP_METHODS.contains(operationEntry.getKey())) {
                continue;
            }

            JsonNode parameters = operationEntry.getValue().path("parameters");

            if (!parameters.isArray()) {
                continue;
            }

            for (JsonNode parameter : parameters) {
                String name = parameter.path("name").asText("");
                if (name.matches("arg\\d+")) {
                    compilerGeneratedParameterNames
                        .add(operationEntry.getKey().toUpperCase() + " " + path + " -> " + name);
                }
            }
        }
    }

    private void collectOperationsWithoutFailureResponses(String path, JsonNode pathItem,
        List<String> operationsWithoutFailureResponses) {
        for (Map.Entry<String, JsonNode> operationEntry : pathItem.properties()) {
            String method = operationEntry.getKey();
            if (!HTTP_METHODS.contains(method)) {
                continue;
            }

            JsonNode responses = operationEntry.getValue().path("responses");
            boolean hasFailureResponse = false;
            for (Map.Entry<String, JsonNode> responseEntry : responses.properties()) {
                if (responseEntry.getKey().startsWith("4") || responseEntry.getKey().startsWith("5")) {
                    hasFailureResponse = true;
                    break;
                }
            }

            if (!hasFailureResponse) {
                operationsWithoutFailureResponses.add(method.toUpperCase() + " " + path);
            }
        }
    }

    private void collectFailureResponsesWithoutExamples(String path, JsonNode pathItem,
        List<String> failureResponsesWithoutExamples) {
        for (Map.Entry<String, JsonNode> operationEntry : pathItem.properties()) {
            String method = operationEntry.getKey();
            if (!HTTP_METHODS.contains(method)) {
                continue;
            }

            JsonNode responses = operationEntry.getValue().path("responses");
            for (Map.Entry<String, JsonNode> responseEntry : responses.properties()) {
                String responseCode = responseEntry.getKey();
                if (!responseCode.startsWith("4") && !responseCode.startsWith("5")) {
                    continue;
                }

                if (!hasSuccessFalseExample(responseEntry.getValue())) {
                    failureResponsesWithoutExamples.add(method.toUpperCase() + " " + path + " -> " + responseCode);
                }
            }
        }
    }

    private boolean hasSuccessFalseExample(JsonNode response) {
        JsonNode mediaType = response.path("content").path("application/json");
        JsonNode example = mediaType.path("example");
        if (hasSuccessFalse(example)) {
            return true;
        }

        JsonNode examples = mediaType.path("examples");
        for (Map.Entry<String, JsonNode> exampleEntry : examples.properties()) {
            if (hasSuccessFalse(exampleEntry.getValue().path("value"))) {
                return true;
            }
        }

        return false;
    }

    private boolean hasSuccessFalse(JsonNode example) {
        if (example.path("success").isBoolean()) {
            return !example.path("success").asBoolean();
        }

        return example.asText("").contains("\"success\": false");
    }
}
