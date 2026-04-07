package com.expensys.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Google Gemini API (works on Render and other cloud hosts with {@code GEMINI_API_KEY}).
 * Free tier: see https://ai.google.dev/pricing
 */
@Service
public class GeminiService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-2.0-flash}")
    private String model;

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Optional<String> generate(String prompt) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(prompt)) {
            return Optional.empty();
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model
                    + ":generateContent?key="
                    + URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);

            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode content = objectMapper.createObjectNode();
            ArrayNode parts = objectMapper.createArrayNode();
            ObjectNode part = objectMapper.createObjectNode();
            part.put("text", prompt);
            parts.add(part);
            content.set("parts", parts);
            ArrayNode contents = objectMapper.createArrayNode();
            contents.add(content);
            root.set("contents", contents);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(45))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            JsonNode body = objectMapper.readTree(response.body());
            JsonNode candidates = body.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                return Optional.empty();
            }
            JsonNode partsNode = candidates.get(0).path("content").path("parts");
            if (!partsNode.isArray() || partsNode.isEmpty()) {
                return Optional.empty();
            }
            JsonNode textNode = partsNode.get(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(textNode.asText().trim());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
