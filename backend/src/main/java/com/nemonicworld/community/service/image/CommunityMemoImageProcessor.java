package com.nemonicworld.community.service.image;

import com.nemonicworld.common.exception.BadRequestException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CommunityMemoImageProcessor {

    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String PNG_FORMAT = "png";
    private static final String INVALID_IMAGE_MESSAGE = "Invalid community memo image file.";

    private final CommunityMemoImageStorage imageStorage;
    private final int whiteThreshold;

    public CommunityMemoImageProcessor(CommunityMemoImageStorage imageStorage,
        @Value("${nemonic.community.memo.image.white-threshold:245}") int whiteThreshold) {
        this.imageStorage = imageStorage;
        this.whiteThreshold = Math.max(0, Math.min(255, whiteThreshold));
    }

    public CommunityMemoImageDerivative process(UUID memoId, String originalObjectKey, String thumbnailObjectKey) {
        String bodyDerivativeObjectKey = createDerivativeObjectKey(memoId, "body.png");
        String thumbnailDerivativeObjectKey = createDerivativeObjectKey(memoId, "thumbnail.png");

        try {
            byte[] bodyBytes = toTransparentPng(imageStorage.download(originalObjectKey));
            imageStorage.upload(bodyDerivativeObjectKey, bodyBytes, PNG_CONTENT_TYPE);

            byte[] thumbnailBytes = toTransparentPng(imageStorage.download(thumbnailObjectKey));
            imageStorage.upload(thumbnailDerivativeObjectKey, thumbnailBytes, PNG_CONTENT_TYPE);
        } catch (RuntimeException e) {
            imageStorage.deleteQuietly(bodyDerivativeObjectKey);
            imageStorage.deleteQuietly(thumbnailDerivativeObjectKey);
            throw e;
        }

        return new CommunityMemoImageDerivative(bodyDerivativeObjectKey, thumbnailDerivativeObjectKey);
    }

    public void deleteQuietly(CommunityMemoImageDerivative derivative) {
        if (derivative == null) {
            return;
        }

        imageStorage.deleteQuietly(derivative.bodyObjectKey());
        imageStorage.deleteQuietly(derivative.thumbnailObjectKey());
    }

    byte[] toTransparentPng(byte[] imageBytes) {
        BufferedImage source = readImage(imageBytes);
        BufferedImage transparentImage = new BufferedImage(source.getWidth(), source.getHeight(),
            BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                transparentImage.setRGB(x, y, transparentPixel(source.getRGB(x, y)));
            }
        }

        return writePng(transparentImage);
    }

    private BufferedImage readImage(byte[] imageBytes) {
        if (imageBytes == null) {
            throw new BadRequestException(INVALID_IMAGE_MESSAGE);
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new BadRequestException(INVALID_IMAGE_MESSAGE);
            }

            return image;
        } catch (IOException e) {
            throw new BadRequestException(INVALID_IMAGE_MESSAGE);
        }
    }

    private int transparentPixel(int argb) {
        int alpha = alpha(argb);
        if (alpha == 0 || !isWhiteLike(argb)) {
            return argb;
        }

        return argb & 0x00ffffff;
    }

    private boolean isWhiteLike(int argb) {
        return red(argb) >= whiteThreshold && green(argb) >= whiteThreshold && blue(argb) >= whiteThreshold;
    }

    private int alpha(int argb) {
        return (argb >>> 24) & 0xff;
    }

    private int red(int argb) {
        return (argb >>> 16) & 0xff;
    }

    private int green(int argb) {
        return (argb >>> 8) & 0xff;
    }

    private int blue(int argb) {
        return argb & 0xff;
    }

    private byte[] writePng(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, PNG_FORMAT, outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException(INVALID_IMAGE_MESSAGE);
        }
    }

    private String createDerivativeObjectKey(UUID memoId, String fileName) {
        return "community/memos/%s/%s".formatted(memoId, fileName);
    }
}
