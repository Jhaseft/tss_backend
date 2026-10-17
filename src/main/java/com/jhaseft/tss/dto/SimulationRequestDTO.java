package com.jhaseft.tss.dto;

public class SimulationRequestDTO {
    private Long questionId;
    private Integer iterations = 1000;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public Integer getIterations() { return iterations; }
    public void setIterations(Integer iterations) { this.iterations = iterations; }
}
