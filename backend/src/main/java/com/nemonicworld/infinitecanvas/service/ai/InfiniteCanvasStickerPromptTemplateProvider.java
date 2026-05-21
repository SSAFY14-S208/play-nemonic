package com.nemonicworld.infinitecanvas.service.ai;

import com.nemonicworld.gms.entity.GmsPrompt;
import com.nemonicworld.gms.repository.GmsPromptRepository;
import org.springframework.stereotype.Service;

@Service
public class InfiniteCanvasStickerPromptTemplateProvider {

    public static final String FEATURE_TYPE_STICKER = "sticker";
    public static final String SOURCE_DATABASE = "database";
    public static final String SOURCE_DEFAULT = "default";

    private static final String DEFAULT_PROMPT_TEMPLATE = """
        너는 협업 무한 캔버스에서 사용할 투명 배경 PNG 스티커를 생성하는 이미지 프롬프트 작성자다.
        사용자의 요청을 귀엽고 선명한 단일 오브젝트 스티커로 만든다.
        배경은 투명하게 유지하고, 여백은 적당히 남기며, 텍스트나 워터마크는 넣지 않는다.
        폭력적이거나 선정적이거나 저작권 캐릭터를 직접 모사하는 요청은 안전하고 일반적인 스티커 표현으로 바꾼다.
        """;

    private final GmsPromptRepository gmsPromptRepository;

    public InfiniteCanvasStickerPromptTemplateProvider(GmsPromptRepository gmsPromptRepository) {
        this.gmsPromptRepository = gmsPromptRepository;
    }

    public CurrentStickerPrompt resolveCurrent() {
        return gmsPromptRepository.findCurrentByFeatureType(FEATURE_TYPE_STICKER).map(this::fromPrompt)
            .orElseGet(this::defaultPrompt);
    }

    private CurrentStickerPrompt fromPrompt(GmsPrompt prompt) {
        return new CurrentStickerPrompt(prompt.getContent(), String.valueOf(prompt.getId()), SOURCE_DATABASE, prompt);
    }

    private CurrentStickerPrompt defaultPrompt() {
        return new CurrentStickerPrompt(DEFAULT_PROMPT_TEMPLATE, SOURCE_DEFAULT, SOURCE_DEFAULT, null);
    }

    public record CurrentStickerPrompt(String template, String version, String source, GmsPrompt prompt) {
    }
}
