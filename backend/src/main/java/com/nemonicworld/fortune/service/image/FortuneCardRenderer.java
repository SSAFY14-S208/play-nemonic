package com.nemonicworld.fortune.service.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 갤러리 썸네일과 공유에 사용할 오늘의 운세 카드 이미지를 생성합니다.
 */
@Component
public class FortuneCardRenderer {

    private static final String FILE_STORAGE_ERROR_MESSAGE = "파일 저장소 처리 중 오류가 발생했습니다.";
    private static final String FONT_RESOURCE_PATH = "/fonts/NanumGothic-Regular.ttf";
    private static final String FONT_LOAD_ERROR_MESSAGE = "운세 카드 폰트를 불러올 수 없습니다.";
    private static final int CARD_WIDTH = 900;
    private static final int CARD_HEIGHT = 1200;
    private static final int CARD_PADDING = 72;

    private final Font baseFont;

    public FortuneCardRenderer() {
        this(loadBundledFont());
    }

    FortuneCardRenderer(Font baseFont) {
        this.baseFont = baseFont;
    }

    public byte[] render(FortuneGmsResult result, JsonNode saju) {
        BufferedImage image = new BufferedImage(CARD_WIDTH, CARD_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        try {
            drawCard(graphics, result, saju);
            return toPngBytes(image);
        } finally {
            graphics.dispose();
        }
    }

    private void drawCard(Graphics2D graphics, FortuneGmsResult result, JsonNode saju) {
        Color background = parseColor(result.bgColor(), new Color(245, 241, 232));
        Color accent = parseColor(result.accentColor(), new Color(80, 105, 150));
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(background);
        graphics.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);

        graphics.setColor(new Color(255, 255, 255, 225));
        graphics.fill(new RoundRectangle2D.Double(48, 48, CARD_WIDTH - 96, CARD_HEIGHT - 96, 42, 42));

        graphics.setColor(accent);
        graphics.fill(new RoundRectangle2D.Double(CARD_PADDING, CARD_PADDING, 96, 18, 9, 9));
        graphics.setFont(cardFont(Font.BOLD, 32));
        graphics.drawString("오늘의 운세", CARD_PADDING, 150);

        graphics.setFont(cardFont(Font.BOLD, 54));
        drawWrappedText(graphics, result.title(), CARD_PADDING, 235, CARD_WIDTH - CARD_PADDING * 2, 62);

        graphics.setFont(cardFont(Font.PLAIN, 30));
        drawWrappedText(graphics, result.summary(), CARD_PADDING, 380, CARD_WIDTH - CARD_PADDING * 2, 42);

        graphics.setColor(accent);
        graphics.setFont(cardFont(Font.BOLD, 40));
        drawWrappedText(graphics, result.postitLine(), CARD_PADDING, 610, CARD_WIDTH - CARD_PADDING * 2, 50);

        graphics.setColor(new Color(42, 42, 42));
        graphics.setFont(cardFont(Font.BOLD, 28));
        graphics.drawString("종합 " + result.overallLuck(), CARD_PADDING, 760);
        graphics.drawString("애정 " + result.loveLuck(), CARD_PADDING + 190, 760);
        graphics.drawString("일/학업 " + result.workLuck(), CARD_PADDING + 380, 760);
        graphics.drawString("금전 " + result.moneyLuck(), CARD_PADDING + 610, 760);

        graphics.setFont(cardFont(Font.PLAIN, 26));
        graphics.drawString("행운의 색  " + result.luckyColor(), CARD_PADDING, 845);
        graphics.drawString("행운의 키워드  " + result.luckyKeyword(), CARD_PADDING, 890);
        graphics.drawString("행운의 방향  " + result.luckyDirection(), CARD_PADDING, 935);

        if (StringUtils.hasText(result.caution())) {
            graphics.setFont(cardFont(Font.PLAIN, 24));
            drawWrappedText(graphics, "주의  " + result.caution(), CARD_PADDING, 1010, CARD_WIDTH - CARD_PADDING * 2, 34);
        }

        graphics.setFont(cardFont(Font.PLAIN, 22));
        graphics.setColor(new Color(90, 90, 90));
        graphics.drawString(createSajuLine(saju), CARD_PADDING, 1100);
    }

    private Font cardFont(int style, int size) {
        return baseFont.deriveFont(style, (float) size);
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

    private void drawWrappedText(Graphics2D graphics, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics metrics = graphics.getFontMetrics();
        int cursorY = y;
        for (String line : wrap(text, metrics, maxWidth)) {
            graphics.drawString(line, x, cursorY);
            cursorY += lineHeight;
        }
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

    private String createSajuLine(JsonNode saju) {
        return "사주  년 %s · 월 %s · 일 %s · 시 %s".formatted(text(saju, "yearPillar"), text(saju, "monthPillar"),
            text(saju, "dayPillar"), text(saju, "hourPillar"));
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);

        return value == null || value.isMissingNode() || value.isNull() ? "-" : value.asText("-");
    }

    private Color parseColor(String hex, Color fallback) {
        try {
            return Color.decode(hex);
        } catch (RuntimeException e) {
            return fallback;
        }
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
