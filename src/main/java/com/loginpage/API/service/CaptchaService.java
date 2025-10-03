package com.loginpage.API.service;

import com.loginpage.API.dto.CaptchaResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class CaptchaService {

    @Value("${google.recaptcha.secret}")
    private String recaptchaSecret;

    private static final String VERIFY_URL =
            "https://www.google.com/recaptcha/api/siteverify";

    public boolean verifyCaptcha(String captchaResponse) {
        RestTemplate restTemplate = new RestTemplate();
        String url = VERIFY_URL + "?secret=" + recaptchaSecret + "&response=" + captchaResponse;

        CaptchaResponse response = restTemplate.postForObject(url, null, CaptchaResponse.class);

        return response != null && response.isSuccess();
    }
}
