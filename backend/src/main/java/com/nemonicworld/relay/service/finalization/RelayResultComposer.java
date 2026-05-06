package com.nemonicworld.relay.service.finalization;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 같은 canvasIndex의 FACE, BODY, LEGS 이미지를 세로로 합성합니다.
 */
@Component
public class RelayResultComposer {

    private static final String IMAGE_READ_ERROR_MESSAGE = "릴레이 파트 이미지를 읽을 수 없습니다.";
    private static final String IMAGE_WRITE_ERROR_MESSAGE = "릴레이 최종 이미지를 생성할 수 없습니다.";
    private static final RelayDrawingPart[] PART_ORDER = {RelayDrawingPart.FACE, RelayDrawingPart.BODY,
        RelayDrawingPart.LEGS};

    private final int defaultPartWidth;
    private final int defaultPartHeight;
    private final int thumbnailMaxSize;

    public RelayResultComposer(@Value("${nemonic.relay.finalization.default-part-width:512}") int defaultPartWidth,
        @Value("${nemonic.relay.finalization.default-part-height:512}") int defaultPartHeight,
        @Value("${nemonic.relay.finalization.thumbnail-max-size:512}") int thumbnailMaxSize) {
        this.defaultPartWidth = Math.max(1, defaultPartWidth);
        this.defaultPartHeight = Math.max(1, defaultPartHeight);
        this.thumbnailMaxSize = Math.max(1, thumbnailMaxSize);
    }

    /**
     * 파트 이미지를 FACE/BODY/LEGS 순서로 세로 합성하고 썸네일도 함께 생성합니다.
     */
    public RelayComposedImage compose(Map<RelayDrawingPart, byte[]> partImages) {
        Map<RelayDrawingPart, BufferedImage> images = readImages(partImages);
        int width = resolveWidth(images);
        int blankHeight = resolveBlankHeight(images);
        int totalHeight = resolveTotalHeight(images, blankHeight);

        BufferedImage original = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = original.createGraphics();
        try {
            applyQualityRenderingHints(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, totalHeight);
            drawParts(graphics, images, width, blankHeight);
        } finally {
            graphics.dispose();
        }

        BufferedImage thumbnail = createThumbnail(original);

        return new RelayComposedImage(toPngBytes(original), toPngBytes(thumbnail));
    }

    /**
     * ImageIO로 읽을 수 있는 제출 이미지만 BufferedImage로 변환합니다.
     */
    private Map<RelayDrawingPart, BufferedImage> readImages(Map<RelayDrawingPart, byte[]> partImages) {
        Map<RelayDrawingPart, BufferedImage> images = new EnumMap<>(RelayDrawingPart.class);
        if (partImages == null) {
            return images;
        }

        for (Map.Entry<RelayDrawingPart, byte[]> entry : partImages.entrySet()) {
            byte[] bytes = entry.getValue();
            if (bytes == null || bytes.length == 0) {
                continue;
            }

            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null) {
                    throw new IllegalStateException(IMAGE_READ_ERROR_MESSAGE);
                }
                images.put(entry.getKey(), image);
            } catch (IOException e) {
                throw new IllegalStateException(IMAGE_READ_ERROR_MESSAGE, e);
            }
        }

        return images;
    }

    /**
     * 사용 가능한 파트 중 가장 넓은 이미지를 최종 결과물 폭으로 사용합니다.
     */
    private int resolveWidth(Map<RelayDrawingPart, BufferedImage> images) {
        return images.values().stream().mapToInt(BufferedImage::getWidth).max().orElse(defaultPartWidth);
    }

    /**
     * 빈 파트 영역 높이는 제출된 파트들의 평균 높이 또는 기본 높이로 정합니다.
     */
    private int resolveBlankHeight(Map<RelayDrawingPart, BufferedImage> images) {
        return (int) Math
            .round(images.values().stream().mapToInt(BufferedImage::getHeight).average().orElse(defaultPartHeight));
    }

    /**
     * FACE/BODY/LEGS 전체를 쌓았을 때 필요한 원본 이미지 높이를 계산합니다.
     */
    private int resolveTotalHeight(Map<RelayDrawingPart, BufferedImage> images, int blankHeight) {
        int totalHeight = 0;
        for (RelayDrawingPart part : PART_ORDER) {
            BufferedImage image = images.get(part);
            totalHeight += image == null ? blankHeight : image.getHeight();
        }

        return totalHeight;
    }

    /**
     * 각 파트를 세로로 그리고, 빈 파트는 흰 영역으로 남깁니다.
     */
    private void drawParts(Graphics2D graphics, Map<RelayDrawingPart, BufferedImage> images, int width,
        int blankHeight) {
        int y = 0;
        for (RelayDrawingPart part : PART_ORDER) {
            BufferedImage image = images.get(part);
            if (image == null) {
                y += blankHeight;
                continue;
            }

            int drawWidth = Math.min(width, image.getWidth());
            int drawHeight = image.getHeight();
            if (image.getWidth() > width) {
                drawHeight = Math.max(1, (int) Math.round((double) image.getHeight() * width / image.getWidth()));
            }
            int x = Math.max(0, (width - drawWidth) / 2);
            graphics.drawImage(image, x, y, drawWidth, drawHeight, null);
            y += drawHeight;
        }
    }

    /**
     * 최종 원본 비율을 유지하면서 긴 변 기준 썸네일 크기로 줄입니다.
     */
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

    /**
     * 축소/리사이즈 품질을 높이기 위한 Graphics2D 렌더링 옵션을 적용합니다.
     */
    private void applyQualityRenderingHints(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /**
     * 합성된 BufferedImage를 MinIO에 저장할 PNG byte 배열로 변환합니다.
     */
    private byte[] toPngBytes(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(IMAGE_WRITE_ERROR_MESSAGE, e);
        }
    }
}
