package com.nemonicworld.share.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemonicworld.gallery.dto.response.GalleryDetailResponse;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ShareEventLoggerTest {

    private final ShareEventLogger shareEventLogger = new ShareEventLogger();

    @Test
    void logShareCreatedDoesNotExposeRawShareToken(CapturedOutput output) {
        GalleryDetailResponse galleryItem = new GalleryDetailResponse("gallery-1", "artifact-1", "fortune", "thumb.png",
            "content.png", null, Map.of(), LocalDateTime.now(), LocalDateTime.now());

        shareEventLogger.logShareCreated("9ca64f4d-1a70-4e42-b3e0-711c1e3bd27b", "raw-share-token-that-must-not-leak",
            galleryItem, "fortune_result");

        assertThat(output).contains("\"event_name\":\"share_link_created\"").contains("share_token_hash")
            .doesNotContain("raw-share-token-that-must-not-leak").doesNotContain("\"share_token\"");
    }
}
