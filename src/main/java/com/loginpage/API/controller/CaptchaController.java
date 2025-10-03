package com.loginpage.API.controller;

import com.loginpage.API.utility.CaptchaGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;   // <-- USE jakarta for Spring Boot 3
// import javax.servlet.http.HttpSession; // <-- USE this for Spring Boot 2

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RestController
public class CaptchaController {

    public static final String CAPTCHA_SESSION_KEY = "CAPTCHA_SESSION_KEY";

    @GetMapping(value = "/captcha", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getCaptcha(HttpSession session) throws IOException {
        // generate text + image
        String text = CaptchaGenerator.randomText(6);
        BufferedImage image = CaptchaGenerator.createImage(text, 200, 60);

        // store expected value in session (lowercase for case-insensitive compare)
        session.setAttribute(CAPTCHA_SESSION_KEY, text.toLowerCase());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setCacheControl("no-store, no-cache, must-revalidate, max-age=0");

            return ResponseEntity.ok().headers(headers).body(bytes);
        }
    }
}
