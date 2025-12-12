package com.loginpage.API.controller;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Controller
public class HomeController {

    // ✅ Use only the raw API key
    private static final String API_KEY = "e1ed8dbf1a464d5fae6facade33eedfd";
    private static final String WSJ_URL =
            "https://newsapi.org/v2/everything?domains=wsj.com&apiKey=" + API_KEY;

    // ✅ HOME PAGE
    @GetMapping("/dashboard")
    public String home(Model model) {
        List<Map<String, Object>> articles = new ArrayList<>();

        try {
            RestTemplate restTemplate = new RestTemplate();

            // Get API response safely
            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                    WSJ_URL,
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> response = responseEntity.getBody();

            if (response != null) {
                Object articlesObj = response.get("articles");
                if (articlesObj instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> castedArticles = (List<Map<String, Object>>) articlesObj;
                    articles = castedArticles;
                }
            }

        } catch (Exception e) {
            // ✅ fallback article if API fails
            articles = List.of(
                    Map.of(
                            "title", "Example WSJ Article",
                            "description", "Could not fetch live WSJ data. This is a sample article.",
                            "url", "https://www.wsj.com",
                            "imageUrl", "https://via.placeholder.com/400x200",
                            "publishedAt", new Date().toString()
                    )
            );
        }

        model.addAttribute("articles", articles);
        return "home";
    }

    // Redirect root to dashboard to avoid mapping errors when users hit links to '/'
    // NOTE: removed root ('/') redirect to avoid mapping conflict with LoginController which
    // already maps '/' to the login page. Keeping '/' mapped to LoginController.

    // ✅ ABOUT PAGE
    @GetMapping("/about")
    public String about() {
        return "about";
    }

    // ✅ CONTACT PAGE
    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    // ✅ LOGOUT REDIRECT
    @GetMapping("/logout")
    public String logout() {
        return "redirect:/";
    }
}
