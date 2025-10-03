package com.loginpage.API.utility;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

public class CaptchaGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Random rand = new Random();

    public static String randomText(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(rand.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    public static BufferedImage createImage(String text, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();

        // background
        g2d.setColor(new Color(230, 230, 255));
        g2d.fillRect(0, 0, width, height);

        // noise lines
        for (int i = 0; i < 8; i++) {
            g2d.setColor(new Color(rand.nextInt(200), rand.nextInt(200), rand.nextInt(200)));
            int x1 = rand.nextInt(width), y1 = rand.nextInt(height);
            int x2 = rand.nextInt(width), y2 = rand.nextInt(height);
            g2d.drawLine(x1, y1, x2, y2);
        }

        // text - use logical font to avoid missing-font problems
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font font = new Font("SansSerif", Font.BOLD, height - 8);
        g2d.setFont(font);

        int x = 8;
        for (char c : text.toCharArray()) {
            g2d.setColor(new Color(rand.nextInt(120), rand.nextInt(120), rand.nextInt(120)));
            double angle = (rand.nextDouble() - 0.5) * 0.5;
            g2d.rotate(angle, x + 10, height / 2.0);
            g2d.drawString(String.valueOf(c), x, (int) (height * 0.75));
            g2d.rotate(-angle, x + 10, height / 2.0);
            x += (width - 16) / text.length();
        }

        // dots
        for (int i = 0; i < 60; i++) {
            g2d.setColor(new Color(rand.nextInt(255), rand.nextInt(255), rand.nextInt(255)));
            int rx = rand.nextInt(width), ry = rand.nextInt(height);
            g2d.fillOval(rx, ry, 2, 2);
        }

        g2d.dispose();
        return img;
    }
}
