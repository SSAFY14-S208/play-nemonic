package com.nemonicworld.artifact.service.download;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.nemonicworld.common.exception.FileStorageException;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

@Component
public class ArtifactQrComposer {

    private static final String FILE_STORAGE_ERROR_MESSAGE = "파일 저장소 처리 중 오류가 발생했습니다.";
    private static final String GIF_FORMAT = "gif";
    private static final String JPG_FORMAT = "jpg";
    private static final int DEFAULT_GIF_DELAY_CS = 10;
    private static final int QR_BASE_SIZE = 160;
    private static final int QR_MIN_SIZE = 72;
    private static final int QR_PADDING = 16;

    public byte[] compose(String sourceContentType, byte[] sourceBytes, String qrUrl) {
        if ("image/gif".equalsIgnoreCase(sourceContentType)) {
            return composeGif(sourceBytes, qrUrl);
        }

        return composeStillImage(sourceBytes, qrUrl);
    }

    private byte[] composeStillImage(byte[] sourceBytes, String qrUrl) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (source == null) {
                throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, null);
            }

            BufferedImage composed = toRgb(overlayQr(source, qrUrl));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(composed, JPG_FORMAT, output);

            return output.toByteArray();
        } catch (IOException | WriterException e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    private byte[] composeGif(byte[] sourceBytes, String qrUrl) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(GIF_FORMAT);
        if (!readers.hasNext()) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, null);
        }

        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(sourceBytes));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            reader.setInput(input, false);
            ImageWriter writer = ImageIO.getImageWritersByFormatName(GIF_FORMAT).next();
            writer.setOutput(imageOutput);
            writer.prepareWriteSequence(null);

            int frameCount = reader.getNumImages(true);
            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                BufferedImage frame = overlayQr(reader.read(frameIndex), qrUrl);
                int delayCentiseconds = readGifDelayCentiseconds(reader.getImageMetadata(frameIndex));
                IIOMetadata metadata = createGifMetadata(writer, delayCentiseconds, frameIndex == 0);
                writer.writeToSequence(new IIOImage(frame, null, metadata), null);
            }

            writer.endWriteSequence();
            writer.dispose();
            imageOutput.flush();

            return output.toByteArray();
        } catch (IOException | WriterException e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        } finally {
            reader.dispose();
        }
    }

    private BufferedImage overlayQr(BufferedImage source, String qrUrl) throws WriterException {
        BufferedImage base = toArgb(source);
        int qrSize = calculateQrSize(base.getWidth(), base.getHeight());
        BufferedImage qrImage = createQrImage(qrUrl, qrSize);
        int x = Math.max(QR_PADDING, base.getWidth() - qrSize - QR_PADDING);
        int y = Math.max(QR_PADDING, base.getHeight() - qrSize - QR_PADDING);

        Graphics2D graphics = base.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.drawImage(qrImage, x, y, null);
        } finally {
            graphics.dispose();
        }

        return base;
    }

    private BufferedImage toArgb(BufferedImage source) {
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        return converted;
    }

    private BufferedImage toRgb(BufferedImage source) {
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, converted.getWidth(), converted.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        return converted;
    }

    private BufferedImage createQrImage(String value, int size) throws WriterException {
        Map<EncodeHintType, Object> hints = Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hints);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }

        return image;
    }

    private int calculateQrSize(int width, int height) {
        int shortestSide = Math.max(1, Math.min(width, height));

        return Math.max(QR_MIN_SIZE, Math.min(QR_BASE_SIZE, shortestSide / 4));
    }

    private int readGifDelayCentiseconds(IIOMetadata metadata) {
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
        IIOMetadataNode control = findNode(root, "GraphicControlExtension");
        if (control == null) {
            return DEFAULT_GIF_DELAY_CS;
        }

        try {
            int parsed = Integer.parseInt(control.getAttribute("delayTime"));
            return parsed <= 0 ? DEFAULT_GIF_DELAY_CS : parsed;
        } catch (NumberFormatException e) {
            return DEFAULT_GIF_DELAY_CS;
        }
    }

    private IIOMetadata createGifMetadata(ImageWriter writer, int delayCentiseconds, boolean firstFrame)
        throws IOException {
        ImageTypeSpecifier imageType = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB);
        IIOMetadata metadata = writer.getDefaultImageMetadata(imageType, null);
        String metadataFormat = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadataFormat);
        IIOMetadataNode control = getOrCreateNode(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "FALSE");
        control.setAttribute("delayTime", Integer.toString(delayCentiseconds));
        control.setAttribute("transparentColorIndex", "0");

        if (firstFrame) {
            IIOMetadataNode appExtensions = getOrCreateNode(root, "ApplicationExtensions");
            IIOMetadataNode appExtension = new IIOMetadataNode("ApplicationExtension");
            appExtension.setAttribute("applicationID", "NETSCAPE");
            appExtension.setAttribute("authenticationCode", "2.0");
            appExtension.setUserObject(new byte[]{0x1, 0x0, 0x0});
            appExtensions.appendChild(appExtension);
        }

        metadata.setFromTree(metadataFormat, root);

        return metadata;
    }

    private IIOMetadataNode getOrCreateNode(IIOMetadataNode root, String name) {
        IIOMetadataNode found = findNode(root, name);
        if (found != null) {
            return found;
        }

        IIOMetadataNode created = new IIOMetadataNode(name);
        root.appendChild(created);

        return created;
    }

    private IIOMetadataNode findNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (name.equals(root.item(i).getNodeName())) {
                return (IIOMetadataNode) root.item(i);
            }
        }

        return null;
    }
}
