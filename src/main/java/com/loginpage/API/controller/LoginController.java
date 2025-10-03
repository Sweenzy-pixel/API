package com.loginpage.API.controller;

import com.loginpage.API.dto.LoginForm;
import com.loginpage.API.model.User;
import com.loginpage.API.model.Article;
import com.loginpage.API.repository.UserRepository;
import com.loginpage.API.service.NewsService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.loginpage.API.service.CaptchaService;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Controller
public class LoginController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NewsService newsService;

    @Autowired
    private CaptchaService captchaService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ------------------- LOGIN -------------------
    @GetMapping("/")
    public String showLoginForm(Model model) {
        model.addAttribute("loginForm", new LoginForm());
        return "login";
    }

    
   @PostMapping("/")
public String processLogin(@ModelAttribute("loginForm") LoginForm form,
                           @RequestParam("g-recaptcha-response") String captchaResponse,
                           Model model) {

    // ✅ First validate reCAPTCHA
    if (!captchaService.verifyCaptcha(captchaResponse)) {
        model.addAttribute("error", "Captcha verification failed. Please try again.");
        return "login";
    }

    // ✅ Then validate username & password
    return userRepository.findByUsername(form.getUsername())
            .map(user -> {
                if (passwordEncoder.matches(form.getPassword(), user.getPassword())) {
                    return "redirect:/home?success=true";
                } else {
                    model.addAttribute("error", "Invalid password");
                    return "login";
                }
            })
            .orElseGet(() -> {
                model.addAttribute("error", "User not found");
                return "login";
            });

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

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);

        return "redirect:/?registered";
    }

    // ------------------- HOME -------------------
    @GetMapping("/home")
    public String homePage(@RequestParam(required = false) String success, Model model) {
        List<Article> articles = newsService.getTopArticles();
        model.addAttribute("articles", articles);
        model.addAttribute("success", success != null);
        return "home";
    }

    // ------------------- CONTACT -------------------
    @GetMapping("/contact")
    public String showContactForm() {
        return "contact"; 
    }

    @PostMapping("/contact")
    public String submitContactForm(@RequestParam String name,
                                    @RequestParam String email,
                                    @RequestParam String message,
                                    Model model) {
        System.out.println("Contact Form Submitted:");
        System.out.println("Name: " + name);
        System.out.println("Email: " + email);
        System.out.println("Message: " + message);

        model.addAttribute("success", "Thank you, " + name + "! Your message has been received.");
        return "contact";
    }

    // ------------------- ABOUT -------------------
    @GetMapping("/about")
    public String aboutPage() {
        return "about"; 
    }
}
