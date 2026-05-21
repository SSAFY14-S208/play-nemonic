package com.nemonicworld.share.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nemonic.share")
public record ShareProperties(@NotBlank String siteUrl, @NotBlank String tokenSecret) {
}
