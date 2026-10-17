package com.jhaseft.tss.dto;

import java.util.List;

public class MatrixItemStatsDTO {
    private Long questionId;
    private String text;
    private Double mean;
    private List<FrequencyEntryDTO> distribution;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Double getMean() { return mean; }
    public void setMean(Double mean) { this.mean = mean; }

    public List<FrequencyEntryDTO> getDistribution() { return distribution; }
    public void setDistribution(List<FrequencyEntryDTO> distribution) { this.distribution = distribution; }
}
