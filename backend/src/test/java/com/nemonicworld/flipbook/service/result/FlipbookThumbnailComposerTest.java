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

        byte[] thumbnailBytes = composer.compose(rgbPngBytes(4, 2));
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(thumbnailBytes));

        assertThat(thumbnail.getWidth()).isEqualTo(2);
        assertThat(thumbnail.getHeight()).isEqualTo(1);
    }

    @Test
    void composePreservesTransparentBackgroundWithoutResize() throws Exception {
        FlipbookThumbnailComposer composer = new FlipbookThumbnailComposer(10);

        byte[] thumbnailBytes = composer.compose(transparentPngWithPixel(4, 4, 1, 1, Color.RED));
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(thumbnailBytes));

        assertThat(alphaAt(thumbnail, 0, 0)).isZero();
        assertThat(thumbnail.getRGB(1, 1)).isEqualTo(Color.RED.getRGB());
    }

    @Test
    void composePreservesTransparentBackgroundWhenResized() throws Exception {
        FlipbookThumbnailComposer composer = new FlipbookThumbnailComposer(2);

        byte[] thumbnailBytes = composer.compose(transparentPngWithLeftFill(8, 8, Color.RED));
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(thumbnailBytes));

        assertThat(thumbnail.getWidth()).isEqualTo(2);
        assertThat(thumbnail.getHeight()).isEqualTo(2);
        assertThat(alphaAt(thumbnail, thumbnail.getWidth() - 1, 0)).isZero();
    }

    private byte[] rgbPngBytes(int width, int height) throws Exception {
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

    private byte[] transparentPngWithPixel(int width, int height, int pixelX, int pixelY, Color color)
        throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(pixelX, pixelY, color.getRGB());

        return pngBytes(image);
    }

    private byte[] transparentPngWithLeftFill(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width / 2; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }

        return pngBytes(image);
    }

    private byte[] pngBytes(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    private int alphaAt(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
