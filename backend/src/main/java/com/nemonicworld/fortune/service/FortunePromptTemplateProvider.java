package com.nemonicworld.fortune.service;

import com.nemonicworld.gms.entity.GmsPrompt;
import com.nemonicworld.gms.repository.GmsPromptRepository;
import org.springframework.stereotype.Service;

@Service
public class FortunePromptTemplateProvider {

    public static final String FEATURE_TYPE_FORTUNE = "fortune";
    public static final String SOURCE_DATABASE = "database";
    public static final String SOURCE_DEFAULT = "default";

    private static final String DEFAULT_PROMPT_TEMPLATE = """
        프론트엔드 만세력 결과를 바탕으로 오늘의 운세를 생성한다.
        응답은 title, summary, overallLuck, loveLuck, workLuck, moneyLuck, luckyColor, luckyKeyword,
        luckyDirection, caution, postitLine을 포함해야 한다.
        luckyDirection은 동쪽, 서쪽, 남쪽, 북쪽 중 하나로 작성한다.
        cardTheme, bgColor, accentColor, iconKey는 카드 에셋 메타데이터가 없으면 null로 둘 수 있다.
        """;

    private final GmsPromptRepository gmsPromptRepository;

    public FortunePromptTemplateProvider(GmsPromptRepository gmsPromptRepository) {
        this.gmsPromptRepository = gmsPromptRepository;
    }

    public CurrentFortunePrompt resolveCurrent() {
        return gmsPromptRepository.findCurrentByFeatureType(FEATURE_TYPE_FORTUNE).map(this::fromPrompt)
            .orElseGet(this::defaultPrompt);
    }

    public String defaultPromptTemplate() {
        return DEFAULT_PROMPT_TEMPLATE;
    }

    private CurrentFortunePrompt fromPrompt(GmsPrompt prompt) {
        return new CurrentFortunePrompt(prompt.getContent(), String.valueOf(prompt.getId()), SOURCE_DATABASE, prompt);
    }

    private CurrentFortunePrompt defaultPrompt() {
        return new CurrentFortunePrompt(DEFAULT_PROMPT_TEMPLATE, SOURCE_DEFAULT, SOURCE_DEFAULT, null);
    }

    public record CurrentFortunePrompt(String template, String version, String source, GmsPrompt prompt) {
    }
}
