package com.nemonicworld.flipbook.service.result;

import com.nemonicworld.common.exception.InternalServerException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 제출된 프레임 이미지들을 하나의 반복 GIF 바이트 배열로 합성합니다.
 */
@Component
public class FlipbookGifComposer {

    private static final String GIF_FORMAT = "gif";
    private static final String GIF_METADATA_FORMAT = "javax_imageio_gif_image_1.0";
    private static final String RESULT_CREATE_ERROR_MESSAGE = "플립북 결과를 생성할 수 없습니다.";

    private final int frameDelayCentiseconds;

    public FlipbookGifComposer(@Value("${nemonic.flipbook.result.gif-frame-delay-ms:200}") int frameDelayMs) {
        this.frameDelayCentiseconds = Math.max(1, frameDelayMs / 10);
    }

    /**
     * 읽을 수 있는 프레임만 사용해 GIF를 생성합니다.
     */
    public byte[] compose(List<byte[]> frameImageBytes) {
        List<BufferedImage> frames = frameImageBytes.stream().map(this::readImage).toList();
        if (frames.isEmpty()) {
            throw new InternalServerException(RESULT_CREATE_ERROR_MESSAGE);
        }

        BufferedImage firstFrame = frames.get(0);
        int width = firstFrame.getWidth();
        int height = firstFrame.getHeight();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            ImageWriter writer = findGifWriter();
            writer.setOutput(imageOutputStream);
            writer.prepareWriteSequence(null);

            for (BufferedImage frame : frames) {
                writeFrame(writer, normalize(frame, width, height));
            }

            writer.endWriteSequence();
            writer.dispose();
            imageOutputStream.flush();

            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new InternalServerException(RESULT_CREATE_ERROR_MESSAGE, e);
        }
    }

    private BufferedImage readImage(byte[] imageBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new InternalServerException(RESULT_CREATE_ERROR_MESSAGE);
            }

            return image;
        } catch (IOException e) {
            throw new InternalServerException(RESULT_CREATE_ERROR_MESSAGE, e);
        }
    }

    private ImageWriter findGifWriter() {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(GIF_FORMAT);
        if (!writers.hasNext()) {
            throw new InternalServerException(RESULT_CREATE_ERROR_MESSAGE);
        }

        return writers.next();
    }

    private void writeFrame(ImageWriter writer, BufferedImage frame) throws IOException {
        ImageWriteParam params = writer.getDefaultWriteParam();
        ImageTypeSpecifier imageType = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
        IIOMetadata metadata = writer.getDefaultImageMetadata(imageType, params);
        configureGifMetadata(metadata);

        writer.writeToSequence(new IIOImage(frame, null, metadata), params);
    }

    private void configureGifMetadata(IIOMetadata metadata) throws IOException {
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(GIF_METADATA_FORMAT);
        IIOMetadataNode graphicControlExtension = getNode(root, "GraphicControlExtension");
        graphicControlExtension.setAttribute("disposalMethod", "none");
        graphicControlExtension.setAttribute("userInputFlag", "FALSE");
        graphicControlExtension.setAttribute("transparentColorFlag", "FALSE");
        graphicControlExtension.setAttribute("delayTime", Integer.toString(frameDelayCentiseconds));
        graphicControlExtension.setAttribute("transparentColorIndex", "0");

        IIOMetadataNode applicationExtensions = getNode(root, "ApplicationExtensions");
        IIOMetadataNode applicationExtension = new IIOMetadataNode("ApplicationExtension");
        applicationExtension.setAttribute("applicationID", "NETSCAPE");
        applicationExtension.setAttribute("authenticationCode", "2.0");
        applicationExtension.setUserObject(new byte[]{0x1, 0x0, 0x0});
        applicationExtensions.appendChild(applicationExtension);

        metadata.setFromTree(GIF_METADATA_FORMAT, root);
    }

    private IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        for (int i = 0; i < rootNode.getLength(); i++) {
            if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }

        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);

        return node;
    }

    private BufferedImage normalize(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        return target;
    }
}
