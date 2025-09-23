package com.loginpage.API.controller;

import com.loginpage.API.model.User;
import com.loginpage.API.repository.PasswordResetTokenRepository;
import com.loginpage.API.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@Controller
public class ResetPasswordController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Show form
    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, Model model) {
        return tokenRepository.findByToken(token)
                .filter(t -> t.getExpiryDate().isAfter(LocalDateTime.now())) 
                .map(t -> {
                    model.addAttribute("token", token);
                    return "reset-password"; 
                })
                .orElse("reset-password-invalid"); 
    }

    // Process form
    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String token,
            @RequestParam String password) {
        return tokenRepository.findByToken(token)
                .filter(t -> t.getExpiryDate().isAfter(LocalDateTime.now()))
                .map(t -> {
                    User user = t.getUser();
                    user.setPassword(passwordEncoder.encode(password));
                    userRepository.save(user);

                    tokenRepository.delete(t); 

                    return "redirect:/login?resetSuccess";
                })
                .orElse("redirect:/login?resetFailed");
    }
}
