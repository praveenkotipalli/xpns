package com.expensys.model.response;

public class AiQueryResponse {
    private String answer;

    public AiQueryResponse(String answer) {
        this.answer = answer;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
