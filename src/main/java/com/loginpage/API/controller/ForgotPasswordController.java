package com.loginpage.API.controller;

import com.loginpage.API.model.PasswordResetToken;
import com.loginpage.API.repository.PasswordResetTokenRepository;
import com.loginpage.API.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;   // <-- USE jakarta for Spring Boot 3
import java.time.LocalDateTime;
import java.util.UUID;

@Controller
public class ForgotPasswordController {

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

    public static final String CAPTCHA_SESSION_KEY = "CAPTCHA_SESSION_KEY";

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email,
                                        @RequestParam String captcha,
                                        HttpSession session,
                                        Model model) {

        // ✅ Validate captcha first
        String expectedCaptcha = (String) session.getAttribute(CAPTCHA_SESSION_KEY);
        if (expectedCaptcha == null || !expectedCaptcha.equalsIgnoreCase(captcha)) {
            model.addAttribute("message", "Invalid captcha. Please try again.");
            return "forgot-password";
        }

        // ✅ Continue only if captcha passes
        return userRepository.findByEmail(email)
                .map(user -> {
                    resetTokenRepository.findByUser(user).ifPresent(resetTokenRepository::delete);

                    String token = UUID.randomUUID().toString();

                    PasswordResetToken resetToken = new PasswordResetToken();
                    resetToken.setToken(token);
                    resetToken.setUser(user);
                    resetToken.setExpiryDate(LocalDateTime.now().plusHours(1));
                    resetTokenRepository.save(resetToken);

                    String resetLink = "http://localhost:8080/reset-password?token=" + token;

                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setTo(email);
                    message.setSubject("Password Reset Request");
                    message.setText("Click the link to reset your password: " + resetLink);

                    mailSender.send(message);

                    model.addAttribute("message", "We have sent a reset link to your email.");
                    return "forgot-password";
                })
                .orElseGet(() -> {
                    model.addAttribute("message", "No account found with that email.");
                    return "forgot-password";
                });
    }
}
