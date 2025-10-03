package com.loginpage.API.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class OpenCaptchaService {

    private static final String VERIFY_URL = "https://api.opencaptcha.io/captcha";

    public boolean verifyCaptcha(String token, String guess) {
        RestTemplate restTemplate = new RestTemplate();

        Map<String, String> request = new HashMap<>();
        request.put("token", token);
        request.put("guess", guess);

        try {
            Boolean response = restTemplate.postForObject(VERIFY_URL, request, Boolean.class);
            return response != null && response;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
