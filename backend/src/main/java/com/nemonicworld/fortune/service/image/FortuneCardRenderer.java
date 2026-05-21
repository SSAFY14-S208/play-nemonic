package com.nemonicworld.fortune.service.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.fortune.service.FortuneLuckyColorResolver;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 갤러리와 공유에 사용할 오늘의 운세 템플릿 카드 이미지를 생성합니다.
 */
@Component
public class FortuneCardRenderer {

    private static final String FILE_STORAGE_ERROR_MESSAGE = "파일 저장소 처리 중 오류가 발생했습니다.";
    private static final String FONT_RESOURCE_PATH = "/fonts/GangwonEduModu-Bold.ttf";
    private static final String TEMPLATE_RESOURCE_PATH = "/fortune/templates/daily-fortune-card.png";
    private static final String ARROW_RESOURCE_PATH = "/fortune/templates/arrow.png";
    private static final String FONT_LOAD_ERROR_MESSAGE = "운세 카드 폰트를 불러올 수 없습니다.";
    private static final String IMAGE_LOAD_ERROR_MESSAGE = "운세 카드 템플릿 이미지를 불러올 수 없습니다.";
    private static final int CARD_WIDTH = 771;
    private static final int CARD_HEIGHT = 895;
    private final Font baseFont;
    private final BufferedImage templateImage;
    private final BufferedImage arrowImage;

    public FortuneCardRenderer() {
        this(loadBundledFont(), loadBundledImage(TEMPLATE_RESOURCE_PATH), loadBundledImage(ARROW_RESOURCE_PATH));
    }

    FortuneCardRenderer(Font baseFont) {
        this(baseFont, loadBundledImage(TEMPLATE_RESOURCE_PATH), loadBundledImage(ARROW_RESOURCE_PATH));
    }

    FortuneCardRenderer(Font baseFont, BufferedImage templateImage, BufferedImage arrowImage) {
        this.baseFont = baseFont;
        this.templateImage = templateImage;
        this.arrowImage = arrowImage;
    }

    public byte[] render(FortuneGmsResult result, JsonNode saju) {
        BufferedImage image = new BufferedImage(CARD_WIDTH, CARD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        try {
            drawCard(graphics, result, saju);
            return toPngBytes(image);
        } finally {
            graphics.dispose();
        }
    }

    private void drawCard(Graphics2D graphics, FortuneGmsResult result, JsonNode saju) {
        configureGraphics(graphics);
        graphics.drawImage(templateImage, 0, 0, CARD_WIDTH, CARD_HEIGHT, null);
        graphics.setColor(new Color(21, 17, 10));

        graphics.setFont(cardFont(34));
        drawCenteredText(graphics, formatFortuneDate(saju), CARD_WIDTH / 2, 82);

        graphics.setFont(cardFont(39));
        drawCenteredWrappedText(graphics, result.title(), CARD_WIDTH / 2, 245, 520, 50, 2);

        graphics.setColor(new Color(75, 56, 35));
        graphics.setFont(cardFont(21));
        drawCenteredWrappedText(graphics, result.postitLine(), CARD_WIDTH / 2, 376, 540, 30, 2);

        drawScores(graphics, result);
        drawLuckySection(graphics, result);

        graphics.setColor(new Color(21, 17, 10));
        graphics.setFont(cardFont(18));
        drawCenteredWrappedText(graphics, result.caution(), 585, 783, 265, 28, 3);
    }

    private void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private void drawScores(Graphics2D graphics, FortuneGmsResult result) {
        graphics.setFont(cardFont(36));
        drawScore(graphics, result.loveLuck(), 141, new Color(255, 95, 149));
        drawScore(graphics, result.workLuck(), 313, new Color(22, 169, 238));
        drawScore(graphics, result.moneyLuck(), 473, new Color(255, 150, 0));
        drawScore(graphics, result.overallLuck(), 642, new Color(60, 132, 36));
    }

    private void drawScore(Graphics2D graphics, int score, int centerX, Color color) {
        graphics.setColor(color);
        drawCenteredText(graphics, String.valueOf(score), centerX, 604);
    }

    private void drawLuckySection(Graphics2D graphics, FortuneGmsResult result) {
        drawLuckyColorSwatch(graphics, 135, 769, 33, resolveLuckyColor(result.luckyColor(), result.title()));
        drawRotatedImage(graphics, arrowImage, 310, 769, 69, 59, directionRotation(result.luckyDirection()));

        graphics.setColor(new Color(21, 17, 10));
        graphics.setFont(cardFont(21));
        drawCenteredText(graphics, result.luckyColor(), 135, 840);
        drawCenteredText(graphics, result.luckyDirection(), 310, 840);
    }

    private void drawLuckyColorSwatch(Graphics2D graphics, int centerX, int centerY, int radius, Color color) {
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(color);
        graphics.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        graphics.setStroke(new java.awt.BasicStroke(8));
        graphics.setColor(new Color(255, 255, 255, 190));
        graphics.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        graphics.setStroke(new java.awt.BasicStroke(2));
        graphics.setColor(new Color(40, 40, 40, 45));
        graphics.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
    }

    private void drawRotatedImage(Graphics2D graphics, BufferedImage image, int centerX, int centerY, int width,
        int height, double rotationDegrees) {
        graphics.translate(centerX, centerY);
        graphics.rotate(Math.toRadians(rotationDegrees));
        graphics.drawImage(image, -width / 2, -height / 2, width, height, null);
        graphics.rotate(-Math.toRadians(rotationDegrees));
        graphics.translate(-centerX, -centerY);
    }

    private double directionRotation(String direction) {
        String value = direction == null ? "" : direction;
        if (value.contains("북동")) {
            return -45;
        }
        if (value.contains("남동")) {
            return 45;
        }
        if (value.contains("남서")) {
            return 135;
        }
        if (value.contains("북서")) {
            return -135;
        }
        if (value.contains("북")) {
            return -90;
        }
        if (value.contains("남")) {
            return 90;
        }
        if (value.contains("서")) {
            return 180;
        }

        return 0;
    }

    private Color resolveLuckyColor(String luckyColor, String seedSource) {
        return Color.decode(FortuneLuckyColorResolver.resolveHex(luckyColor, seedSource));
    }

    private String formatFortuneDate(JsonNode saju) {
        String date = text(saju, "fortuneDate");
        if (!StringUtils.hasText(date)) {
            date = text(saju, "date");
        }
        if (!StringUtils.hasText(date)) {
            return "";
        }

        try {
            LocalDate localDate = LocalDate.parse(date);
            return "%d/%d (%s)".formatted(localDate.getMonthValue(), localDate.getDayOfMonth(),
                koreanWeekday(localDate.getDayOfWeek()));
        } catch (RuntimeException e) {
            return date;
        }
    }

    private String koreanWeekday(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    private Font cardFont(int size) {
        return baseFont.deriveFont(Font.BOLD, (float) size);
    }

    static Font loadBundledFont() {
        try (InputStream inputStream = FortuneCardRenderer.class.getResourceAsStream(FONT_RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException(FONT_LOAD_ERROR_MESSAGE + ": " + FONT_RESOURCE_PATH);
            }

            Font font = Font.createFont(Font.TRUETYPE_FONT, inputStream);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);

            return font;
        } catch (FontFormatException | IOException e) {
            throw new IllegalStateException(FONT_LOAD_ERROR_MESSAGE, e);
        }
    }

    private static BufferedImage loadBundledImage(String resourcePath) {
        try (InputStream inputStream = FortuneCardRenderer.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException(IMAGE_LOAD_ERROR_MESSAGE + ": " + resourcePath);
            }

            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IllegalStateException(IMAGE_LOAD_ERROR_MESSAGE + ": " + resourcePath);
            }

            return image;
        } catch (IOException e) {
            throw new IllegalStateException(IMAGE_LOAD_ERROR_MESSAGE, e);
        }
    }

    private void drawCenteredWrappedText(Graphics2D graphics, String text, int centerX, int centerY, int maxWidth,
        int lineHeight, int maxLines) {
        List<String> lines = wrap(String.valueOf(text), graphics.getFontMetrics(), maxWidth);
        List<String> visibleLines = lines.subList(0, Math.min(lines.size(), maxLines));
        int startY = centerY - ((visibleLines.size() - 1) * lineHeight) / 2;

        for (int index = 0; index < visibleLines.size(); index++) {
            String line = visibleLines.get(index);
            boolean isLastVisibleLine = index == maxLines - 1 && lines.size() > maxLines;
            drawCenteredText(graphics, isLastVisibleLine ? line.replaceAll("[.。…]*$", "") + "..." : line, centerX,
                startY + index * lineHeight);
        }
    }

    private void drawCenteredText(Graphics2D graphics, String text, int centerX, int centerY) {
        FontMetrics metrics = graphics.getFontMetrics();
        String value = String.valueOf(text);
        int x = centerX - metrics.stringWidth(value) / 2;
        int y = centerY - (metrics.getAscent() + metrics.getDescent()) / 2 + metrics.getAscent();
        graphics.drawString(value, x, y);
    }

    private List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = String.valueOf(text).split("\\s+");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (metrics.stringWidth(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }

            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
            line.setLength(0);
            line.append(word);
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }

        return lines;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);

        return value == null || value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private byte[] toPngBytes(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);

            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }
}
