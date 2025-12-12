package com.loginpage.API.utility;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Random;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

/**
 * Utility class for generating and verifying 2FA codes (TOTP)
 */
public class TwoFactorAuthUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String ISSUER = "MyLoginApp";

    /**
     * Generate a Base32 secret key (Google Authenticator compatible)
     */
    public static String generateSecretKey() {
        byte[] buffer = new byte[10];
        new Random().nextBytes(buffer);
        return base32Encode(buffer);
    }

    /**
     * Create an otpauth:// URL recognized by Google Authenticator
     */
    public static String getOtpAuthURL(String username, String secretKey) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
                ISSUER, username, secretKey, ISSUER);
    }

    /**
     * Generate a QR code data URI (to embed directly in <img th:src>)
     */
    public static String getQrCodeDataUri(String username, String secretKey) {
        try {
            String otpAuth = getOtpAuthURL(username, secretKey);
            QRCodeWriter qrWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrWriter.encode(otpAuth, BarcodeFormat.QR_CODE, 250, 250);

            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (WriterException | java.io.IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Verify if a 6-digit TOTP code is valid
     */
    public static boolean verifyCode(String secretKey, int code) {
        long timeWindow = Instant.now().getEpochSecond() / 30;
        try {
            for (int i = -1; i <= 1; i++) { // allow +/- 30s drift
                long calc = generateTOTP(secretKey, timeWindow + i);
                if (calc == code) return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Generate a 6-digit TOTP using the secret key
     */
    private static long generateTOTP(String secretKey, long timeIndex)
            throws NoSuchAlgorithmException, InvalidKeyException {

        byte[] key = base32Decode(secretKey);
        byte[] data = new byte[8];
        long value = timeIndex;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>= 8;
        }

        SecretKeySpec signKey = new SecretKeySpec(key, HMAC_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(signKey);
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0xF;
        long truncated = 0;
        for (int i = 0; i < 4; i++) {
            truncated <<= 8;
            truncated |= (hash[offset + i] & 0xFF);
        }
        truncated &= 0x7FFFFFFF;
        return truncated % 1_000_000;
    }

    // --- Helper methods for Base32 encoding/decoding ---
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = data[0];
        int next = 1;
        int bitsLeft = 8;
        while (bitsLeft > 0 || next < data.length) {
            if (bitsLeft < 5) {
                if (next < data.length) {
                    buffer <<= 8;
                    buffer |= (data[next++] & 0xFF);
                    bitsLeft += 8;
                } else {
                    int pad = 5 - bitsLeft;
                    buffer <<= pad;
                    bitsLeft += pad;
                }
            }
            int index = (buffer >> (bitsLeft - 5)) & 0x1F;
            bitsLeft -= 5;
            result.append(BASE32_CHARS.charAt(index));
        }
        return result.toString();
    }

    private static byte[] base32Decode(String base32) {
        int buffer = 0, bitsLeft = 0, count = 0;
        byte[] result = new byte[base32.length() * 5 / 8];
        for (char c : base32.toCharArray()) {
            int val = BASE32_CHARS.indexOf(Character.toUpperCase(c));
            if (val < 0) continue;
            buffer <<= 5;
            buffer |= val & 31;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[count++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        if (count < result.length) {
            byte[] shorter = new byte[count];
            System.arraycopy(result, 0, shorter, 0, count);
            return shorter;
        }
        return result;
    }
}
