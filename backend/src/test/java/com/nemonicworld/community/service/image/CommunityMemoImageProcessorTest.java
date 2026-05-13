package com.nemonicworld.community.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nemonicworld.common.exception.BadRequestException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CommunityMemoImageProcessorTest {

    private final CommunityMemoImageStorage imageStorage = mock(CommunityMemoImageStorage.class);
    private final CommunityMemoImageProcessor processor = new CommunityMemoImageProcessor(imageStorage, 245);

    @Test
    void toTransparentPngMakesEveryWhiteLikePixelTransparentIncludingInteriorWhite() throws Exception {
        BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                image.setRGB(x, y, Color.WHITE.getRGB());
            }
        }
        for (int i = 0; i < 5; i++) {
            image.setRGB(i, 0, Color.BLACK.getRGB());
            image.setRGB(i, 4, Color.BLACK.getRGB());
            image.setRGB(0, i, Color.BLACK.getRGB());
            image.setRGB(4, i, Color.BLACK.getRGB());
        }
        image.setRGB(2, 2, Color.WHITE.getRGB());

        BufferedImage result = readPng(processor.toTransparentPng(toPng(image)));

        assertThat(alpha(result, 2, 2)).isZero();
        assertThat(alpha(result, 1, 1)).isZero();
        assertThat(alpha(result, 0, 0)).isEqualTo(255);
        assertThat(result.getRGB(0, 0) & 0x00ffffff).isEqualTo(Color.BLACK.getRGB() & 0x00ffffff);
    }

    @Test
    void toTransparentPngKeepsColoredPixelsAndExistingTransparentPixels() throws Exception {
        BufferedImage image = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, new Color(255, 255, 255, 255).getRGB());
        image.setRGB(1, 0, new Color(30, 140, 220, 180).getRGB());
        image.setRGB(2, 0, new Color(255, 255, 255, 0).getRGB());

        BufferedImage result = readPng(processor.toTransparentPng(toPng(image)));

        assertThat(alpha(result, 0, 0)).isZero();
        assertThat(alpha(result, 1, 0)).isEqualTo(180);
        assertThat(result.getRGB(1, 0) & 0x00ffffff).isEqualTo(new Color(30, 140, 220).getRGB() & 0x00ffffff);
        assertThat(alpha(result, 2, 0)).isZero();
    }

    @Test
    void toTransparentPngUsesConfiguredWhiteThreshold() throws Exception {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, new Color(245, 245, 245, 255).getRGB());
        image.setRGB(1, 0, new Color(244, 244, 244, 255).getRGB());

        BufferedImage result = readPng(processor.toTransparentPng(toPng(image)));

        assertThat(alpha(result, 0, 0)).isZero();
        assertThat(alpha(result, 1, 0)).isEqualTo(255);
    }

    @Test
    void processStoresBodyAndThumbnailDerivativesAsPng() throws Exception {
        UUID memoId = UUID.randomUUID();
        byte[] sourcePng = toPng(singlePixel(Color.WHITE));
        when(imageStorage.download("uploads/body.png")).thenReturn(sourcePng);
        when(imageStorage.download("uploads/thumb.png")).thenReturn(sourcePng);

        CommunityMemoImageDerivative derivative = processor.process(memoId, "uploads/body.png", "uploads/thumb.png");

        assertThat(derivative.bodyObjectKey()).isEqualTo("community/memos/%s/body.png".formatted(memoId));
        assertThat(derivative.thumbnailObjectKey()).isEqualTo("community/memos/%s/thumbnail.png".formatted(memoId));
        verify(imageStorage).upload(eq(derivative.bodyObjectKey()), any(byte[].class), eq("image/png"));
        verify(imageStorage).upload(eq(derivative.thumbnailObjectKey()), any(byte[].class), eq("image/png"));
    }

    @Test
    void processDeletesDerivativeObjectsWhenUploadFails() throws Exception {
        UUID memoId = UUID.randomUUID();
        byte[] sourcePng = toPng(singlePixel(Color.BLACK));
        when(imageStorage.download("uploads/body.png")).thenReturn(sourcePng);
        when(imageStorage.download("uploads/thumb.png")).thenReturn(sourcePng);
        doThrow(new IllegalStateException("upload failed")).when(imageStorage)
            .upload(eq("community/memos/%s/thumbnail.png".formatted(memoId)), any(byte[].class), eq("image/png"));

        assertThatThrownBy(() -> processor.process(memoId, "uploads/body.png", "uploads/thumb.png"))
            .isInstanceOf(IllegalStateException.class);

        verify(imageStorage).deleteQuietly("community/memos/%s/body.png".formatted(memoId));
        verify(imageStorage).deleteQuietly("community/memos/%s/thumbnail.png".formatted(memoId));
    }

    @Test
    void toTransparentPngRejectsInvalidImageBytes() {
        assertThatThrownBy(() -> processor.toTransparentPng("not an image".getBytes()))
            .isInstanceOf(BadRequestException.class);
    }

    private BufferedImage singlePixel(Color color) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color.getRGB());

        return image;
    }

    private byte[] toPng(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);

            return outputStream.toByteArray();
        }
    }

    private BufferedImage readPng(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xff;
    }
}
