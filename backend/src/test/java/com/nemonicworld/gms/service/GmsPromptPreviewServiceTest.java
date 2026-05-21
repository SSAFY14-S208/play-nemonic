package com.nemonicworld.gms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import com.nemonicworld.fortune.service.FortuneGenerationService;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import com.nemonicworld.fortune.service.image.FortuneCardRenderer;
import com.nemonicworld.gms.dto.request.GmsPromptPreviewRequest;
import com.nemonicworld.gms.dto.response.GmsPromptPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GmsPromptPreviewServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FortuneGenerationService fortuneGenerationService;

    @Mock
    private FortuneCardRenderer fortuneCardRenderer;

    private GmsPromptPreviewService service;

    @BeforeEach
    void setUp() {
        service = new GmsPromptPreviewService(fortuneGenerationService, fortuneCardRenderer);
    }

    @Test
    void previewReturnsFortuneResultAndBase64Image() {
        FortuneCreateRequest sampleSaju = sampleSaju();
        JsonNode saju = objectMapper.valueToTree(sampleSaju);
        FortuneGmsResult result = sampleGmsResult();
        when(fortuneGenerationService.validateAndGetSaju(sampleSaju)).thenReturn(saju);
        when(fortuneGenerationService.generatePreview("Candidate prompt", saju)).thenReturn(result);
        when(fortuneGenerationService.toFortuneResult(result)).thenCallRealMethod();
        when(fortuneGenerationService.toSajuInfo(saju)).thenCallRealMethod();
        when(fortuneGenerationService.toFortuneDesign(result)).thenCallRealMethod();
        when(fortuneCardRenderer.render(result, saju)).thenReturn(new byte[]{1, 2, 3});

        GmsPromptPreviewResponse response = service
            .preview(new GmsPromptPreviewRequest("fortune", "Candidate prompt", sampleSaju));

        assertThat(response.featureType()).isEqualTo("fortune");
        assertThat(response.fortune().title()).isEqualTo("Preview title");
        assertThat(response.fortune().luckyDirection()).isEqualTo("East");
        assertThat(response.saju().yearPillar()).isEqualTo("gapja");
        assertThat(response.design().bgColor()).isEqualTo("#F5F1E8");
        assertThat(response.previewImageBase64()).isEqualTo("data:image/png;base64,AQID");
    }

    @Test
    void previewRejectsUnsupportedFeatureTypeBeforeGeneration() {
        GmsPromptPreviewRequest request = new GmsPromptPreviewRequest("sticker", "Candidate prompt", sampleSaju());

        assertThatThrownBy(() -> service.preview(request)).isInstanceOf(BadRequestException.class);

        verifyNoInteractions(fortuneGenerationService, fortuneCardRenderer);
    }

    @Test
    void previewRejectsBlankContentBeforeGeneration() {
        GmsPromptPreviewRequest request = new GmsPromptPreviewRequest("fortune", " ", sampleSaju());

        assertThatThrownBy(() -> service.preview(request)).isInstanceOf(BadRequestException.class);

        verifyNoInteractions(fortuneGenerationService, fortuneCardRenderer);
    }

    private FortuneCreateRequest sampleSaju() {
        return new FortuneCreateRequest("solar", "gapja", "byeongin", "mujin", "gengo", "wood", "earth", "yang",
            "yang");
    }

    private FortuneGmsResult sampleGmsResult() {
        return new FortuneGmsResult("Preview title", "Preview summary", 80, 70, 65, 90, "Blue", "Focus", "East",
            "Move slowly", "Stay calm today", "default", "#F5F1E8", "#506996", "sun");
    }
}
