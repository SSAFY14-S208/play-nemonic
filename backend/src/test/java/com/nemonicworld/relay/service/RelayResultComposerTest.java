package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.service.finalization.RelayComposedImage;
import com.nemonicworld.relay.service.finalization.RelayResultComposer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class RelayResultComposerTest {

    private final RelayResultComposer composer = new RelayResultComposer(4, 3, 4);

    @Test
    void composeStacksFaceBodyAndLegsVertically() throws Exception {
        Map<RelayDrawingPart, byte[]> partImages = new EnumMap<>(RelayDrawingPart.class);
        partImages.put(RelayDrawingPart.FACE, png(4, 2, Color.RED));
        partImages.put(RelayDrawingPart.BODY, png(4, 3, Color.GREEN));
        partImages.put(RelayDrawingPart.LEGS, png(4, 4, Color.BLUE));

        RelayComposedImage result = composer.compose(partImages);

        BufferedImage original = read(result.originalPng());
        assertThat(original.getWidth()).isEqualTo(4);
        assertThat(original.getHeight()).isEqualTo(9);
        assertThat(original.getRGB(1, 0)).isEqualTo(Color.RED.getRGB());
        assertThat(original.getRGB(1, 2)).isEqualTo(Color.GREEN.getRGB());
        assertThat(original.getRGB(1, 5)).isEqualTo(Color.BLUE.getRGB());

        BufferedImage thumbnail = read(result.thumbnailPng());
        assertThat(Math.max(thumbnail.getWidth(), thumbnail.getHeight())).isEqualTo(4);
    }

    @Test
    void composeUsesBlankAreaForEmptyParts() throws Exception {
        Map<RelayDrawingPart, byte[]> partImages = new EnumMap<>(RelayDrawingPart.class);
        partImages.put(RelayDrawingPart.BODY, png(4, 3, Color.GREEN));

        RelayComposedImage result = composer.compose(partImages);

        BufferedImage original = read(result.originalPng());
        assertThat(original.getWidth()).isEqualTo(4);
        assertThat(original.getHeight()).isEqualTo(9);
        assertThat(alphaAt(original, 1, 0)).isZero();
        assertThat(original.getRGB(1, 3)).isEqualTo(Color.GREEN.getRGB());
        assertThat(alphaAt(original, 1, 6)).isZero();
    }

    @Test
    void composeOverlapsPartsWithUpperPartPriority() throws Exception {
        RelayResultComposer overlapComposer = new RelayResultComposer(4, 10, 100, 2);
        Map<RelayDrawingPart, byte[]> partImages = new EnumMap<>(RelayDrawingPart.class);
        partImages.put(RelayDrawingPart.FACE, png(4, 10, Color.RED));
        partImages.put(RelayDrawingPart.BODY, png(4, 10, Color.GREEN));
        partImages.put(RelayDrawingPart.LEGS, png(4, 10, Color.BLUE));

        RelayComposedImage result = overlapComposer.compose(partImages);

        BufferedImage original = read(result.originalPng());
        assertThat(original.getWidth()).isEqualTo(4);
        assertThat(original.getHeight()).isEqualTo(26);
        assertThat(original.getRGB(1, 8)).isEqualTo(Color.RED.getRGB());
        assertThat(original.getRGB(1, 9)).isEqualTo(Color.RED.getRGB());
        assertThat(original.getRGB(1, 16)).isEqualTo(Color.GREEN.getRGB());
        assertThat(original.getRGB(1, 17)).isEqualTo(Color.GREEN.getRGB());
        assertThat(original.getRGB(1, 18)).isEqualTo(Color.BLUE.getRGB());
    }

    @Test
    void composeKeepsBlankPartTransparentEvenWhenOverlapped() throws Exception {
        RelayResultComposer overlapComposer = new RelayResultComposer(4, 10, 100, 2);
        Map<RelayDrawingPart, byte[]> partImages = new EnumMap<>(RelayDrawingPart.class);
        partImages.put(RelayDrawingPart.FACE, png(4, 10, Color.RED));
        partImages.put(RelayDrawingPart.LEGS, png(4, 10, Color.BLUE));

        RelayComposedImage result = overlapComposer.compose(partImages);

        BufferedImage original = read(result.originalPng());
        assertThat(original.getHeight()).isEqualTo(26);
        assertThat(alphaAt(original, 1, 12)).isZero();
        assertThat(original.getRGB(1, 16)).isEqualTo(Color.BLUE.getRGB());
        assertThat(original.getRGB(1, 18)).isEqualTo(Color.BLUE.getRGB());
    }

    @Test
    void composeClampsExcessiveOverlap() throws Exception {
        RelayResultComposer overlapComposer = new RelayResultComposer(4, 3, 100, 100);
        Map<RelayDrawingPart, byte[]> partImages = new EnumMap<>(RelayDrawingPart.class);
        partImages.put(RelayDrawingPart.FACE, png(4, 3, Color.RED));
        partImages.put(RelayDrawingPart.BODY, png(4, 3, Color.GREEN));
        partImages.put(RelayDrawingPart.LEGS, png(4, 3, Color.BLUE));

        RelayComposedImage result = overlapComposer.compose(partImages);

        BufferedImage original = read(result.originalPng());
        assertThat(original.getHeight()).isEqualTo(5);
        assertThat(original.getHeight()).isPositive();
    }

    @Test
    void composeCreatesDefaultBlankResultWhenAllPartsAreEmpty() throws Exception {
        RelayComposedImage result = composer.compose(Map.of());

        BufferedImage original = read(result.originalPng());
        assertThat(original.getWidth()).isEqualTo(4);
        assertThat(original.getHeight()).isEqualTo(9);
        assertThat(alphaAt(original, 1, 1)).isZero();
    }

    @Test
    void composePreservesTransparentInputBackground() throws Exception {
        Map<RelayDrawingPart, byte[]> partImages = new EnumMap<>(RelayDrawingPart.class);
        partImages.put(RelayDrawingPart.FACE, transparentPngWithPixel(4, 3, 1, 1, Color.RED));

        RelayComposedImage result = composer.compose(partImages);

        BufferedImage original = read(result.originalPng());
        assertThat(alphaAt(original, 0, 0)).isZero();
        assertThat(original.getRGB(1, 1)).isEqualTo(Color.RED.getRGB());
    }

    @Test
    void composePreservesTransparencyWhenThumbnailIsResized() throws Exception {
        RelayResultComposer smallThumbnailComposer = new RelayResultComposer(10, 10, 5);
        Map<RelayDrawingPart, byte[]> partImages = new EnumMap<>(RelayDrawingPart.class);
        partImages.put(RelayDrawingPart.FACE, transparentPngWithCenterFill(10, 10, Color.RED));

        RelayComposedImage result = smallThumbnailComposer.compose(partImages);

        BufferedImage thumbnail = read(result.thumbnailPng());
        assertThat(Math.max(thumbnail.getWidth(), thumbnail.getHeight())).isEqualTo(5);
        assertThat(alphaAt(thumbnail, 0, thumbnail.getHeight() - 1)).isZero();
    }

    @Test
    void composeReusesOriginalPngBytesWhenThumbnailDoesNotNeedResize() throws Exception {
        RelayResultComposer largeThumbnailComposer = new RelayResultComposer(4, 3, 100);
        Map<RelayDrawingPart, byte[]> partImages = new EnumMap<>(RelayDrawingPart.class);
        partImages.put(RelayDrawingPart.FACE, png(4, 2, Color.RED));
        partImages.put(RelayDrawingPart.BODY, png(4, 3, Color.GREEN));
        partImages.put(RelayDrawingPart.LEGS, png(4, 4, Color.BLUE));

        RelayComposedImage result = largeThumbnailComposer.compose(partImages);

        assertThat(result.thumbnailPng()).isSameAs(result.originalPng());
    }

    private byte[] png(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] transparentPngWithPixel(int width, int height, int pixelX, int pixelY, Color color)
        throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(pixelX, pixelY, color.getRGB());

        return pngBytes(image);
    }

    private byte[] transparentPngWithCenterFill(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
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

    private BufferedImage read(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private int alphaAt(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
