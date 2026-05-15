package com.nemonicworld.flipbook.service.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
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

    @Test
    void composeUsesBackgroundDisposalSoTransparentFramesRemainIndependent() throws Exception {
        FlipbookGifComposer composer = new FlipbookGifComposer(200);

        byte[] gifBytes = composer.compose(
            List.of(transparentPngWithLeftFill(4, 4, Color.RED), transparentPngWithRightFill(4, 4, Color.BLUE)));

        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(gifBytes))) {
            reader.setInput(input);

            assertThat(reader.getNumImages(true)).isEqualTo(2);
            assertThat(frameDisposalMethod(reader, 0)).isEqualTo("restoreToBackgroundColor");
            assertThat(frameDisposalMethod(reader, 1)).isEqualTo("restoreToBackgroundColor");

            BufferedImage secondFrame = reader.read(1);
            assertThat(alphaAt(secondFrame, 0, 1)).isZero();
            assertThat(secondFrame.getRGB(3, 1)).isEqualTo(Color.BLUE.getRGB());
        } finally {
            reader.dispose();
        }
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

    private byte[] transparentPngWithRightFill(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = width / 2; x < width; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    private String frameDisposalMethod(ImageReader reader, int imageIndex) throws Exception {
        IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(imageIndex)
            .getAsTree("javax_imageio_gif_image_1.0");

        return findNode(root, "GraphicControlExtension").getAttribute("disposalMethod");
    }

    private IIOMetadataNode findNode(IIOMetadataNode rootNode, String nodeName) {
        for (int i = 0; i < rootNode.getLength(); i++) {
            if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }

        throw new IllegalArgumentException("Missing GIF metadata node: " + nodeName);
    }

    private int alphaAt(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
