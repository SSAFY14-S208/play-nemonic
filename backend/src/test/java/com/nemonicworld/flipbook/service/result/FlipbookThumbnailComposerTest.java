package com.nemonicworld.flipbook.service.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * 플립북 결과 썸네일이 원본 비율을 유지하며 축소되는지 검증합니다.
 */
class FlipbookThumbnailComposerTest {

    @Test
    void composeScalesImageByLongestSide() throws Exception {
        FlipbookThumbnailComposer composer = new FlipbookThumbnailComposer(2);

        byte[] thumbnailBytes = composer.compose(pngBytes(4, 2));
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(thumbnailBytes));

        assertThat(thumbnail.getWidth()).isEqualTo(2);
        assertThat(thumbnail.getHeight()).isEqualTo(1);
    }

    private byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);

        return outputStream.toByteArray();
    }
}
