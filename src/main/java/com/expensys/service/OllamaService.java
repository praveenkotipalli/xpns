package com.expensys.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class OllamaService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${ai.ollama.enabled:false}")
    private boolean enabled;

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ai.ollama.model:llama3.2:3b}")
    private String model;

    public OllamaService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public Optional<String> generate(String prompt) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("prompt", prompt);
            payload.put("stream", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode answerNode = root.get("response");
            if (answerNode == null || answerNode.asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(answerNode.asText().trim());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
