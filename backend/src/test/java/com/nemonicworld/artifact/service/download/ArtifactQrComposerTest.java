package com.nemonicworld.artifact.service.download;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.junit.jupiter.api.Test;

/**
 * QR 합성기가 정적 이미지와 GIF를 각각 읽을 수 있는 결과물로 만드는지 검증합니다.
 */
class ArtifactQrComposerTest {

    private final ArtifactQrComposer artifactQrComposer = new ArtifactQrComposer();

    /**
     * PNG 원본은 QR 합성 후 다운로드용 JPG로 변환됩니다.
     */
    @Test
    void composeStillImageReturnsReadableJpg() throws Exception {
        byte[] sourceBytes = imageBytes("png");

        byte[] composedBytes = artifactQrComposer.compose("image/png", sourceBytes,
            "https://nemonic.example.com/artifacts/1");

        BufferedImage composed = ImageIO.read(new ByteArrayInputStream(composedBytes));
        assertThat(composed).isNotNull();
        assertThat(composed.getWidth()).isEqualTo(360);
        assertThat(composed.getHeight()).isEqualTo(240);
    }

    @Test
    void composeStillImageFlattensTransparentSourceOnWhiteBackground() throws Exception {
        byte[] sourceBytes = transparentImageBytes("png");

        byte[] composedBytes = artifactQrComposer.compose("image/png", sourceBytes,
            "https://nemonic.example.com/artifacts/1");

        BufferedImage composed = ImageIO.read(new ByteArrayInputStream(composedBytes));
        Color background = new Color(composed.getRGB(10, 10));
        assertThat(background.getRed()).isGreaterThan(240);
        assertThat(background.getGreen()).isGreaterThan(240);
        assertThat(background.getBlue()).isGreaterThan(240);
    }

    /**
     * GIF 원본은 QR 합성 후 다시 GIF로 반환됩니다.
     */
    @Test
    void composeGifReturnsReadableGif() throws Exception {
        byte[] sourceBytes = imageBytes("gif");

        byte[] composedBytes = artifactQrComposer.compose("image/gif", sourceBytes,
            "https://nemonic.example.com/artifacts/1");

        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(composedBytes))) {
            reader.setInput(input);

            assertThat(reader.getNumImages(true)).isEqualTo(1);
            assertThat(reader.read(0).getWidth()).isEqualTo(360);
        } finally {
            reader.dispose();
        }
    }

    @Test
    void composeGifFlattensTransparentFramesOnWhiteBackground() throws Exception {
        byte[] sourceBytes = transparentImageBytes("gif");

        byte[] composedBytes = artifactQrComposer.compose("image/gif", sourceBytes,
            "https://nemonic.example.com/artifacts/1");

        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(composedBytes))) {
            reader.setInput(input);

            BufferedImage frame = reader.read(0);
            Color background = new Color(frame.getRGB(10, 10));
            assertThat(background.getRed()).isEqualTo(255);
            assertThat(background.getGreen()).isEqualTo(255);
            assertThat(background.getBlue()).isEqualTo(255);
        } finally {
            reader.dispose();
        }
    }

    private byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(360, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLUE);
            graphics.fillRect(40, 40, 120, 80);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);

        return output.toByteArray();
    }

    private byte[] transparentImageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(360, 240, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(40, 40, 120, 80);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);

        return output.toByteArray();
    }
}
