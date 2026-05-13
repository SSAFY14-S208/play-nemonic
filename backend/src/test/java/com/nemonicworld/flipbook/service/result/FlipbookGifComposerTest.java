package com.nemonicworld.flipbook.service.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class FlipbookGifComposerTest {

    @Test
    void composeDoesNotPaintTransparentFrameBackgroundWhite() throws Exception {
        FlipbookGifComposer composer = new FlipbookGifComposer(200);

        byte[] gifBytes = composer.compose(List.of(transparentPngWithLeftFill(4, 4, Color.RED)));
        BufferedImage frame = ImageIO.read(new ByteArrayInputStream(gifBytes));

        assertThat(frame.getWidth()).isEqualTo(4);
        assertThat(frame.getHeight()).isEqualTo(4);
        assertThat(frame.getRGB(0, 1)).isEqualTo(Color.RED.getRGB());
        assertThat(frame.getRGB(3, 1)).isNotEqualTo(Color.WHITE.getRGB());
        assertThat(alphaAt(frame, 3, 1)).isZero();
    }

    private byte[] transparentPngWithLeftFill(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width / 2; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    private int alphaAt(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
