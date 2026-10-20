package com.jhaseft.tss.dto;

import java.util.List;

public class QuestionStatsDTO {
    private Long questionId;
    private String text;
    private String type;
    private String section;
    private Integer position;
    private Integer totalAnswered;

    private List<FrequencyEntryDTO> distribution;
    private Double mean;
    private Double stdDev;
    private Double median;
    private Double min;
    private Double max;

    private List<MatrixItemStatsDTO> matrixItems;
    private List<String> sampleResponses;
    private List<FrequencyEntryDTO> keywords;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public Integer getTotalAnswered() { return totalAnswered; }
    public void setTotalAnswered(Integer totalAnswered) { this.totalAnswered = totalAnswered; }

    public List<FrequencyEntryDTO> getDistribution() { return distribution; }
    public void setDistribution(List<FrequencyEntryDTO> distribution) { this.distribution = distribution; }

    public Double getMean() { return mean; }
    public void setMean(Double mean) { this.mean = mean; }

    public Double getStdDev() { return stdDev; }
    public void setStdDev(Double stdDev) { this.stdDev = stdDev; }

    public Double getMedian() { return median; }
    public void setMedian(Double median) { this.median = median; }

    public Double getMin() { return min; }
    public void setMin(Double min) { this.min = min; }

    public Double getMax() { return max; }
    public void setMax(Double max) { this.max = max; }

    public List<MatrixItemStatsDTO> getMatrixItems() { return matrixItems; }
    public void setMatrixItems(List<MatrixItemStatsDTO> matrixItems) { this.matrixItems = matrixItems; }

    public List<String> getSampleResponses() { return sampleResponses; }
    public void setSampleResponses(List<String> sampleResponses) { this.sampleResponses = sampleResponses; }

    public List<FrequencyEntryDTO> getKeywords() { return keywords; }
    public void setKeywords(List<FrequencyEntryDTO> keywords) { this.keywords = keywords; }
}
