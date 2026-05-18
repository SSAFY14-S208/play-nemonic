package com.nemonicworld.fortune.service.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class FortuneCardRendererTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void loadBundledFontCanDisplayKoreanFortuneText() {
        Font font = FortuneCardRenderer.loadBundledFont();

        assertThat(font.canDisplayUpTo("오늘의 운세 임신 경술 계유 을묘 행운의 키워드")).isEqualTo(-1);
    }

    @Test
    void renderCreatesReadablePngWithKoreanText() throws Exception {
        FortuneCardRenderer renderer = new FortuneCardRenderer();

        byte[] pngBytes = renderer.render(sampleResult(), sampleSaju());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));

        assertThat(pngBytes).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(900);
        assertThat(image.getHeight()).isEqualTo(1200);
    }

    private FortuneGmsResult sampleResult() {
        return new FortuneGmsResult("오늘은 흐름을 정리하는 날", "차분하게 우선순위를 세우면 좋은 결과가 나는 하루입니다.", 78, 66, 84, 71, "은회색", "정리",
            "동쪽", "결정은 한 템포 늦추는 것이 좋습니다.", "오늘은 정리할수록 운이 열린다", "moon", "#2C2C4A", "#C0C0C0", "moon_waning");
    }

    private JsonNode sampleSaju() throws Exception {
        return OBJECT_MAPPER.readTree("""
            {
              "yearPillar": "임신",
              "monthPillar": "경술",
              "dayPillar": "계유",
              "hourPillar": "을묘"
            }
            """);
    }
}
