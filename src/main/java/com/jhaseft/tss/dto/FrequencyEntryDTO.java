package com.jhaseft.tss.dto;

public class FrequencyEntryDTO {
    private String label;
    private Long count;
    private Double percentage;
    private Double numericValue;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Long getCount() { return count; }
    public void setCount(Long count) { this.count = count; }

    public Double getPercentage() { return percentage; }
    public void setPercentage(Double percentage) { this.percentage = percentage; }

    public Double getNumericValue() { return numericValue; }
    public void setNumericValue(Double numericValue) { this.numericValue = numericValue; }
}
