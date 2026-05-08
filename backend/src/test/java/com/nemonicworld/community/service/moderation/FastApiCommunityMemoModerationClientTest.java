package com.nemonicworld.community.service.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.community.config.CommunityModerationProperties;
import org.junit.jupiter.api.Test;

class FastApiCommunityMemoModerationClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkAllowsWithoutHttpCallWhenModerationIsDisabled() {
        CommunityModerationProperties properties = new CommunityModerationProperties(false, "://invalid", "/check",
            1000L, 3000L, true);
        FastApiCommunityMemoModerationClient client = new FastApiCommunityMemoModerationClient(properties,
            objectMapper);

        CommunityMemoModerationResult result = client
            .check(new CommunityMemoModerationRequest("http://image", "http://thumb", "텍스트", "DIRECT"));

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void checkAllowsOnClientErrorOnlyWhenFailClosedIsDisabled() {
        CommunityModerationProperties defaultProperties = new CommunityModerationProperties(true, "://invalid",
            "/check", 1000L, 3000L, null);
        FastApiCommunityMemoModerationClient defaultClient = new FastApiCommunityMemoModerationClient(defaultProperties,
            objectMapper);

        CommunityMemoModerationResult defaultResult = defaultClient
            .check(new CommunityMemoModerationRequest("http://image", "http://thumb", "", "GALLERY"));

        assertThat(defaultResult.allowed()).isTrue();

        CommunityModerationProperties openProperties = new CommunityModerationProperties(true, "://invalid", "/check",
            1000L, 3000L, false);
        FastApiCommunityMemoModerationClient openClient = new FastApiCommunityMemoModerationClient(openProperties,
            objectMapper);

        CommunityMemoModerationResult result = openClient
            .check(new CommunityMemoModerationRequest("http://image", "http://thumb", "", "GALLERY"));

        assertThat(result.allowed()).isTrue();

        CommunityModerationProperties closedProperties = new CommunityModerationProperties(true, "://invalid", "/check",
            1000L, 3000L, true);
        FastApiCommunityMemoModerationClient closedClient = new FastApiCommunityMemoModerationClient(closedProperties,
            objectMapper);

        assertThatThrownBy(
            () -> closedClient.check(new CommunityMemoModerationRequest("http://image", "http://thumb", "", "GALLERY")))
            .isInstanceOf(CommunityMemoModerationException.class);
    }
}
