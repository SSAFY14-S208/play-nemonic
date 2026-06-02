package com.nemonicworld.fortune.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FortuneLuckyColorResolverTest {

    @Test
    void resolveFrontendPaletteLuckyColorNames() {
        assertThat(FortuneLuckyColorResolver.resolveHex("라벤더 밀크", "seed")).isEqualTo("#CDB7F6");
        assertThat(FortuneLuckyColorResolver.resolveHex("민트 포그", "seed")).isEqualTo("#9ED8C3");
        assertThat(FortuneLuckyColorResolver.resolveHex("버터 옐로", "seed")).isEqualTo("#EFD27B");
        assertThat(FortuneLuckyColorResolver.resolveHex("코랄 핑크", "seed")).isEqualTo("#EF9AA7");
        assertThat(FortuneLuckyColorResolver.resolveHex("스카이 블루", "seed")).isEqualTo("#91BDE8");
    }

    @Test
    void resolveLuckyColorNameAfterTrimming() {
        assertThat(FortuneLuckyColorResolver.resolveHex(" 스카이 블루 ", "seed")).isEqualTo("#91BDE8");
    }
}
