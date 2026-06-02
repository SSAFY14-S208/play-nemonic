package com.nemonicworld.fortune.service;

import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 운세 행운 색상명을 프론트와 이미지 렌더러가 바로 사용할 수 있는 HEX 색상으로 정규화합니다.
 */
public final class FortuneLuckyColorResolver {

    public static final String SUPPORTED_COLOR_NAMES = "라벤더 밀크, 민트 포그, 버터 옐로, 코랄 핑크, 스카이 블루, "
        + "은회색, 베이지, 살구색, 초록, 파랑, 보라, 분홍, 노랑, 흰색, 검정";

    private static final String[] FALLBACK_COLORS = {"#CDB7F6", "#9ED8C3", "#EFD27B", "#EF9AA7", "#91BDE8"};
    private static final Map<String, String> LUCKY_COLOR_HEX = Map.ofEntries(Map.entry("은회색", "#C0C0C0"),
        Map.entry("라벤더 밀크", "#CDB7F6"), Map.entry("라벤더밀크", "#CDB7F6"), Map.entry("라벤더", "#CDB7F6"),
        Map.entry("민트 포그", "#9ED8C3"), Map.entry("민트포그", "#9ED8C3"), Map.entry("민트", "#9ED8C3"),
        Map.entry("버터 옐로", "#EFD27B"), Map.entry("버터옐로", "#EFD27B"), Map.entry("버터 노랑", "#EFD27B"),
        Map.entry("버터노랑", "#EFD27B"), Map.entry("코랄 핑크", "#EF9AA7"), Map.entry("코랄핑크", "#EF9AA7"),
        Map.entry("코랄", "#EF9AA7"), Map.entry("스카이 블루", "#91BDE8"), Map.entry("스카이블루", "#91BDE8"),
        Map.entry("베이지", "#D7C09A"), Map.entry("베이지색", "#D7C09A"), Map.entry("샌드베이지", "#D7C09A"),
        Map.entry("샌드 베이지", "#D7C09A"), Map.entry("짙은베이지", "#9B7A52"), Map.entry("짙은 베이지", "#9B7A52"),
        Map.entry("진한베이지", "#9B7A52"), Map.entry("진한 베이지", "#9B7A52"), Map.entry("갈색", "#8B5A32"),
        Map.entry("브라운", "#8B5A32"), Map.entry("노랑", "#F4D35E"), Map.entry("노란색", "#F4D35E"),
        Map.entry("보라", "#A281D0"), Map.entry("보라색", "#A281D0"), Map.entry("초록", "#8CCF92"),
        Map.entry("초록색", "#8CCF92"), Map.entry("파랑", "#82B9E6"), Map.entry("파란색", "#82B9E6"),
        Map.entry("하늘색", "#91BDE8"), Map.entry("분홍", "#EF9AA7"), Map.entry("분홍색", "#EF9AA7"),
        Map.entry("흰색", "#F8F6EF"), Map.entry("검정", "#2F2A33"), Map.entry("검은색", "#2F2A33"),
        Map.entry("살구색", "#FFC7A8"));

    private FortuneLuckyColorResolver() {
    }

    public static String resolveHex(String luckyColor, String seedSource) {
        if (StringUtils.hasText(luckyColor)) {
            String normalizedName = luckyColor.trim();
            if (LUCKY_COLOR_HEX.containsKey(normalizedName)) {
                return LUCKY_COLOR_HEX.get(normalizedName);
            }
        }

        int index = Math.floorMod(String.valueOf(seedSource).hashCode(), FALLBACK_COLORS.length);
        return FALLBACK_COLORS[index];
    }
}
