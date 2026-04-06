package com.expensys.controller;

import com.expensys.model.request.AiQueryRequest;
import com.expensys.model.response.AiQueryResponse;
import com.expensys.model.response.ApiResponse;
import com.expensys.service.AiInsightsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
public class AiController {
    private final AiInsightsService aiInsightsService;

    public AiController(AiInsightsService aiInsightsService) {
        this.aiInsightsService = aiInsightsService;
    }

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<AiQueryResponse>> query(@RequestBody AiQueryRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Question cannot be empty"), HttpStatus.BAD_REQUEST);
        }

        String answer = aiInsightsService.answerQuestion(request.getQuestion(), request.getYear(), request.getMonth());
        return new ResponseEntity<>(ApiResponse.success(new AiQueryResponse(answer)), HttpStatus.OK);
    }
}
