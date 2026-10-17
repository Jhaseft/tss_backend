package com.jhaseft.tss.dto;

import java.util.List;

public class AnswerInputDTO {
    private Long questionId;
    private String value;
    private List<String> values;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }
}
