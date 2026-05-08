package com.nemonicworld.flipbook.service.result;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 플립북 대표 프레임을 갤러리용 썸네일 PNG로 축소합니다.
 */
@Component
public class FlipbookThumbnailComposer {

    private static final String THUMBNAIL_CREATE_ERROR_MESSAGE = "플립북 썸네일을 생성할 수 없습니다.";

    private final int thumbnailMaxSize;

    public FlipbookThumbnailComposer(@Value("${nemonic.flipbook.result.thumbnail-max-size:512}") int thumbnailMaxSize) {
        this.thumbnailMaxSize = Math.max(1, thumbnailMaxSize);
    }

    /**
     * 첫 프레임 이미지를 긴 변 기준 썸네일 크기로 줄여 PNG 바이트로 변환합니다.
     */
    public byte[] compose(byte[] imageBytes) {
        BufferedImage original = readImage(imageBytes);
        BufferedImage thumbnail = createThumbnail(original);

        return toPngBytes(thumbnail);
    }

    private BufferedImage readImage(byte[] imageBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new IllegalStateException(THUMBNAIL_CREATE_ERROR_MESSAGE);
            }

            return image;
        } catch (IOException e) {
            throw new IllegalStateException(THUMBNAIL_CREATE_ERROR_MESSAGE, e);
        }
    }

    private BufferedImage createThumbnail(BufferedImage original) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        int longestSide = Math.max(originalWidth, originalHeight);
        if (longestSide <= thumbnailMaxSize) {
            return original;
        }

        double scale = (double) thumbnailMaxSize / longestSide;
        int thumbnailWidth = Math.max(1, (int) Math.round(originalWidth * scale));
        int thumbnailHeight = Math.max(1, (int) Math.round(originalHeight * scale));
        BufferedImage thumbnail = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            applyQualityRenderingHints(graphics);
            graphics.drawImage(original, 0, 0, thumbnailWidth, thumbnailHeight, null);
        } finally {
            graphics.dispose();
        }

        return thumbnail;
    }

    private void applyQualityRenderingHints(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private byte[] toPngBytes(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(THUMBNAIL_CREATE_ERROR_MESSAGE, e);
        }
    }
}
