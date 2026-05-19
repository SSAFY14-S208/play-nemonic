package com.nemonicworld.fortune.service.gms;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * GMS 연동 지점의 기본 구현입니다.
 *
 * <p>
 * 현재 저장소에는 외부 GMS endpoint 계약이 없으므로, 프롬프트 템플릿과 만세력 값을 이용해 구조화된 결과를 생성합니다. 외부
 * GMS 계약이 확정되면 같은 인터페이스 구현체만 교체하면 됩니다.
 */
public class TemplateFortuneGmsClient implements FortuneGmsClient {

    private static final Map<String, Theme> THEMES = Map.of("목", new Theme("forest", "#E9F6EF", "#278A57", "leaf"), "화",
        new Theme("sun", "#FFF2E1", "#E56B2F", "sun_rise"), "토", new Theme("earth", "#F4EAD8", "#A66C2F", "stone"), "금",
        new Theme("moon", "#F1F3F5", "#74808A", "moon_waning"), "수",
        new Theme("water", "#E8F2FF", "#2B72C4", "water_drop"));

    @Override
    public FortuneGmsResult generate(String promptTemplate, JsonNode saju) {
        String dayElemental = text(saju, "dayMasterElement", "수");
        String dayBranchElemental = text(saju, "dayBranchElement", "금");
        String dayYinYang = text(saju, "dayMasterYinYang", "음");
        Theme theme = THEMES.getOrDefault(dayElemental, THEMES.get("수"));
        int seed = Math.abs((String.valueOf(promptTemplate) + saju.toString()).hashCode());

        String keyword = resolveKeyword(dayElemental, dayBranchElemental);
        String title = "오늘은 %s의 흐름을 살리는 날".formatted(keyword);
        String summary = "%s 기운이 중심이 되는 하루입니다. 급하게 밀어붙이기보다 지금 필요한 선택을 차분히 고르면 좋은 결과가 따라옵니다.".formatted(dayElemental);
        String caution = "%s의 균형이 흔들리면 작은 말도 크게 느껴질 수 있으니, 중요한 결정은 한 템포 늦춰보세요.".formatted(dayYinYang);

        return new FortuneGmsResult(title, summary, score(seed, 0), score(seed, 8), score(seed, 16), score(seed, 24),
            resolveLuckyColor(dayElemental), keyword, resolveLuckyDirection(seed), caution,
            "오늘은 %s할수록 운이 열린다".formatted(keyword), theme.cardTheme(), theme.bgColor(), theme.accentColor(),
            theme.iconKey());
    }

    private int score(int seed, int shift) {
        return 55 + Math.floorMod(seed >> shift, 41);
    }

    private String resolveKeyword(String dayElemental, String dayBranchElemental) {
        return switch (dayElemental) {
            case "목" -> "성장";
            case "화" -> "표현";
            case "토" -> "정리";
            case "금" -> "집중";
            case "수" -> "유연함";
            default -> StringUtils.hasText(dayBranchElemental) ? dayBranchElemental : "균형";
        };
    }

    private String resolveLuckyColor(String dayElemental) {
        return switch (dayElemental) {
            case "목" -> "초록";
            case "화" -> "살구색";
            case "토" -> "베이지";
            case "금" -> "은회색";
            case "수" -> "파랑";
            default -> "흰색";
        };
    }

    private String resolveLuckyDirection(int seed) {
        return switch (Math.floorMod(seed, 4)) {
            case 0 -> "동쪽";
            case 1 -> "서쪽";
            case 2 -> "남쪽";
            default -> "북쪽";
        };
    }

    private String text(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node == null ? null : node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText())) {
            return defaultValue;
        }

        return value.asText().trim();
    }

    private record Theme(String cardTheme, String bgColor, String accentColor, String iconKey) {
    }
}
