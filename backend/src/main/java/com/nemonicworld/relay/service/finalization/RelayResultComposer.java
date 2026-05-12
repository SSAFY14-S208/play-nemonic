package com.nemonicworld.relay.service.finalization;

import com.nemonicworld.common.exception.InternalServerException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RelayResultComposer {

    private static final String IMAGE_READ_ERROR_MESSAGE = "릴레이 파트 이미지를 읽을 수 없습니다.";
    private static final String IMAGE_WRITE_ERROR_MESSAGE = "릴레이 최종 이미지를 생성할 수 없습니다.";
    private static final String PART_PLACEMENT_ERROR_MESSAGE = "릴레이 최종 합성 파트 배치 정보를 찾을 수 없습니다.";
    private static final RelayDrawingPart[] DRAW_ORDER = {RelayDrawingPart.LEGS, RelayDrawingPart.BODY,
        RelayDrawingPart.FACE};

    private final int defaultPartWidth;
    private final int defaultPartHeight;
    private final int thumbnailMaxSize;
    private final int overlapHeight;

    public RelayResultComposer(int defaultPartWidth, int defaultPartHeight, int thumbnailMaxSize) {
        this(defaultPartWidth, defaultPartHeight, thumbnailMaxSize, 0);
    }

    @Autowired
    public RelayResultComposer(@Value("${nemonic.relay.finalization.default-part-width:512}") int defaultPartWidth,
        @Value("${nemonic.relay.finalization.default-part-height:512}") int defaultPartHeight,
        @Value("${nemonic.relay.finalization.thumbnail-max-size:512}") int thumbnailMaxSize,
        @Value("${nemonic.relay.finalization.overlap-height:0}") int overlapHeight) {
        this.defaultPartWidth = Math.max(1, defaultPartWidth);
        this.defaultPartHeight = Math.max(1, defaultPartHeight);
        this.thumbnailMaxSize = Math.max(1, thumbnailMaxSize);
        this.overlapHeight = Math.max(0, overlapHeight);
    }

    public RelayComposedImage compose(Map<RelayDrawingPart, byte[]> partImages) {
        Map<RelayDrawingPart, BufferedImage> images = readImages(partImages);
        int width = resolveWidth(images);
        int blankHeight = resolveBlankHeight(images);
        PartPlacement[] placements = resolvePlacements(images, blankHeight);
        int totalHeight = resolveTotalHeight(placements);

        BufferedImage original = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = original.createGraphics();
        try {
            applyQualityRenderingHints(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, totalHeight);
            drawParts(graphics, images, placements, width);
        } finally {
            graphics.dispose();
        }

        BufferedImage thumbnail = createThumbnail(original);

        return new RelayComposedImage(toPngBytes(original), toPngBytes(thumbnail));
    }

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
                    throw new InternalServerException(IMAGE_READ_ERROR_MESSAGE);
                }
                images.put(entry.getKey(), image);
            } catch (IOException e) {
                throw new InternalServerException(IMAGE_READ_ERROR_MESSAGE, e);
            }
        }

        return images;
    }

    private int resolveWidth(Map<RelayDrawingPart, BufferedImage> images) {
        return images.values().stream().mapToInt(BufferedImage::getWidth).max().orElse(defaultPartWidth);
    }

    private int resolveBlankHeight(Map<RelayDrawingPart, BufferedImage> images) {
        return (int) Math
            .round(images.values().stream().mapToInt(BufferedImage::getHeight).average().orElse(defaultPartHeight));
    }

    private PartPlacement[] resolvePlacements(Map<RelayDrawingPart, BufferedImage> images, int blankHeight) {
        int faceHeight = resolvePartHeight(images, RelayDrawingPart.FACE, blankHeight);
        int bodyHeight = resolvePartHeight(images, RelayDrawingPart.BODY, blankHeight);
        int legsHeight = resolvePartHeight(images, RelayDrawingPart.LEGS, blankHeight);
        int faceBodyOverlap = resolvePairOverlap(faceHeight, bodyHeight);
        int bodyLegsOverlap = resolvePairOverlap(bodyHeight, legsHeight);

        int faceY = 0;
        int bodyY = faceHeight - faceBodyOverlap;
        int legsY = bodyY + bodyHeight - bodyLegsOverlap;

        return new PartPlacement[]{new PartPlacement(RelayDrawingPart.FACE, faceY, faceHeight),
            new PartPlacement(RelayDrawingPart.BODY, bodyY, bodyHeight),
            new PartPlacement(RelayDrawingPart.LEGS, legsY, legsHeight)};
    }

    private int resolvePartHeight(Map<RelayDrawingPart, BufferedImage> images, RelayDrawingPart part, int blankHeight) {
        BufferedImage image = images.get(part);

        return image == null ? blankHeight : Math.max(1, image.getHeight());
    }

    private int resolvePairOverlap(int upperHeight, int lowerHeight) {
        if (overlapHeight <= 0) {
            return 0;
        }

        int maxOverlap = Math.min(upperHeight, lowerHeight) - 1;
        if (maxOverlap <= 0) {
            return 0;
        }

        return Math.min(overlapHeight, maxOverlap);
    }

    private int resolveTotalHeight(PartPlacement[] placements) {
        int totalHeight = 0;
        for (PartPlacement placement : placements) {
            totalHeight = Math.max(totalHeight, placement.y() + placement.height());
        }

        return Math.max(1, totalHeight);
    }

    private void drawParts(Graphics2D graphics, Map<RelayDrawingPart, BufferedImage> images, PartPlacement[] placements,
        int width) {
        for (RelayDrawingPart part : DRAW_ORDER) {
            PartPlacement placement = findPlacement(placements, part);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, placement.y(), width, placement.height());

            BufferedImage image = images.get(part);
            if (image == null) {
                continue;
            }

            int drawWidth = Math.min(width, image.getWidth());
            int drawHeight = image.getHeight();
            if (image.getWidth() > width) {
                drawHeight = Math.max(1, (int) Math.round((double) image.getHeight() * width / image.getWidth()));
            }
            int x = Math.max(0, (width - drawWidth) / 2);
            graphics.drawImage(image, x, placement.y(), drawWidth, drawHeight, null);
        }
    }

    private PartPlacement findPlacement(PartPlacement[] placements, RelayDrawingPart part) {
        for (PartPlacement placement : placements) {
            if (placement.part() == part) {
                return placement;
            }
        }

        throw new InternalServerException(PART_PLACEMENT_ERROR_MESSAGE);
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
            throw new InternalServerException(IMAGE_WRITE_ERROR_MESSAGE, e);
        }
    }

    private record PartPlacement(RelayDrawingPart part, int y, int height) {
    }
}
