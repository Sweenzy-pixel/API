package com.loginpage.API.controller;

import com.loginpage.API.dto.LoginForm;
import com.loginpage.API.model.User;
import com.loginpage.API.model.Article;
import com.loginpage.API.repository.UserRepository;
import com.loginpage.API.service.NewsService;
import com.loginpage.API.service.CaptchaService;
import com.loginpage.API.utility.TwoFactorAuthUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.loginpage.API.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
public class LoginController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NewsService newsService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private AuditLogService auditLogService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ------------------- LOGIN PAGE -------------------
    @GetMapping("/")
    public String showLoginForm(Model model) {
        model.addAttribute("loginForm", new LoginForm());
        return "login";
    }

    // ------------------- LOGIN PROCESS -------------------
    @PostMapping("/")
    public String processLogin(@ModelAttribute("loginForm") LoginForm form,
                               @RequestParam("g-recaptcha-response") String captchaResponse,
                               Model model,
                               HttpServletRequest request) {

        // Step 1: Validate captcha
        if (!captchaService.verifyCaptcha(captchaResponse)) {
            model.addAttribute("error", "Captcha verification failed. Please try again.");
            return "login";
        }

        // Step 2: Check username/password
        Optional<User> userOpt = userRepository.findByUsername(form.getUsername());
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "User not found");
            return "login";
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(form.getPassword(), user.getPassword())) {
            model.addAttribute("error", "Invalid password");
            return "login";
        }

        // Step 3: If first login, generate secret and show QR
        if (user.getSecretKey() == null || !user.isTwoFactorEnabled()) {
            String secret = TwoFactorAuthUtil.generateSecretKey();
            user.setSecretKey(secret);
            user.setTwoFactorEnabled(true);
            userRepository.save(user);

            model.addAttribute("username", user.getUsername());
            model.addAttribute("qrUrl", TwoFactorAuthUtil.getQrCodeDataUri(user.getUsername(), secret));
            return "show-qr";
        }

        // Step 4: If 2FA enabled, show OTP page
        if (user.isTwoFactorEnabled()) {
            model.addAttribute("username", user.getUsername());
            model.addAttribute("qrUrl", TwoFactorAuthUtil.getQrCodeDataUri(user.getUsername(), user.getSecretKey()));
            return "verify-2fa";
        }

        // Step 5: Login success (no 2FA)
        auditLogService.logAction(user.getId(), user.getUsername(), "Login", "User logged in successfully", request);
        return "redirect:/home?success=true";
    }

    // ------------------- 2FA OTP PAGE -------------------
    @GetMapping("/verify-2fa")
    public String show2FAPage(@RequestParam String username, Model model) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "User not found");
            return "login";
        }

        User user = userOpt.get();

        // ✅ Always provide QR URL for backup scanning
        model.addAttribute("username", username);
        model.addAttribute("qrUrl", TwoFactorAuthUtil.getQrCodeDataUri(username, user.getSecretKey()));

        return "verify-2fa";
    }

    @PostMapping("/verify-2fa")
    public String verify2FA(@RequestParam String username,
                            @RequestParam int code,
                            Model model,
                            HttpServletRequest request) {

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "User not found");
            return "verify-2fa";
        }

        User user = userOpt.get();

        if (!TwoFactorAuthUtil.verifyCode(user.getSecretKey(), code)) {
            model.addAttribute("username", username);
            model.addAttribute("qrUrl", TwoFactorAuthUtil.getQrCodeDataUri(username, user.getSecretKey()));
            model.addAttribute("error", "Invalid authentication code.");
            return "verify-2fa";
        }

        auditLogService.logAction(user.getId(), user.getUsername(), "Login", "User logged in successfully with 2FA", request);
        return "redirect:/home?success=true";
    }

    // ------------------- SIGNUP -------------------
    @GetMapping("/signup")
    public String showSignupForm(Model model) {
        model.addAttribute("user", new User());
        return "signup";
    }

    @PostMapping("/signup")
    public String processSignup(@ModelAttribute User user, Model model) {

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            model.addAttribute("error", "Username already exists!");
            return "signup";
        }

        // Hash password
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Generate 2FA secret key
        String secret = TwoFactorAuthUtil.generateSecretKey();
        user.setSecretKey(secret);
        user.setTwoFactorEnabled(true);

        userRepository.save(user);

        // Redirect to QR display page
        return "redirect:/show-qr?username=" + user.getUsername();
    }

    // ------------------- SHOW QR PAGE -------------------
    @GetMapping("/show-qr")
    public String showQRCode(@RequestParam String username, Model model) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "User not found");
            return "login";
        }

        User user = userOpt.get();
        model.addAttribute("username", username);
        model.addAttribute("qrUrl", TwoFactorAuthUtil.getQrCodeDataUri(username, user.getSecretKey()));
        return "show-qr";
    }

    // ------------------- HOME -------------------
    @GetMapping("/home")
    public String homePage(@RequestParam(required = false) String success, Model model) {
        try {
            List<Article> articles = newsService.getTopArticles();
            // Ensure articles is never null
            if (articles == null) {
                articles = new ArrayList<>();
            }
            model.addAttribute("articles", articles);
        } catch (Exception e) {
            // If there's an error, provide empty list
            e.printStackTrace();
            model.addAttribute("articles", new ArrayList<Article>());
        }
        model.addAttribute("success", success != null);
        return "home";
    }
}
