package com.jhaseft.tss.dto;

import java.util.List;

public class MatrixItemStatsDTO {
    private Long questionId;
    private String text;
    private Double mean;
    private Double stdDev;
    private List<FrequencyEntryDTO> distribution;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Double getMean() { return mean; }
    public void setMean(Double mean) { this.mean = mean; }

    public Double getStdDev() { return stdDev; }
    public void setStdDev(Double stdDev) { this.stdDev = stdDev; }

    public List<FrequencyEntryDTO> getDistribution() { return distribution; }
    public void setDistribution(List<FrequencyEntryDTO> distribution) { this.distribution = distribution; }
}
