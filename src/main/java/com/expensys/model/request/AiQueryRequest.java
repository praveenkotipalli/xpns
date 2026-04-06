package com.expensys.model.request;

import com.expensys.model.enums.Month;

public class AiQueryRequest {
    private String question;
    private Integer year;
    private Month month;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Month getMonth() {
        return month;
    }

    public void setMonth(Month month) {
        this.month = month;
    }
}
