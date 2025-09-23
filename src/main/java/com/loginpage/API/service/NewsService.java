package com.loginpage.API.service;

import com.loginpage.API.model.Article;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class NewsService {

    @Value("${news.api.key}")
    private String apiKey;

    private static final String NEWS_ENDPOINT =
            "https://newsapi.org/v2/everything?domains=wsj.com&pageSize=10&apiKey=";

    public List<Article> getTopArticles() {
        List<Article> results = new ArrayList<>();
        try {
            String url = NEWS_ENDPOINT + apiKey;
            RestTemplate rest = new RestTemplate();
            String json = rest.getForObject(url, String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode articles = root.path("articles");
            if (articles.isArray()) {
                for (JsonNode n : articles) {
                    Article a = new Article();
                    a.setTitle(n.path("title").asText(null));
                    a.setDescription(n.path("description").asText(null));
                    a.setUrl(n.path("url").asText(null));
                    a.setImageUrl(n.path("urlToImage").asText(null));
                    a.setPublishedAt(n.path("publishedAt").asText(null));
                    a.setSourceName(n.path("source").path("name").asText(null));
                    results.add(a);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }
}
