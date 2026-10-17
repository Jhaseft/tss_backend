package com.jhaseft.tss.dto;

public class HistogramBinDTO {
    private Double midpoint;
    private Integer count;

    public HistogramBinDTO(Double midpoint, Integer count) {
        this.midpoint = midpoint;
        this.count = count;
    }

    public Double getMidpoint() { return midpoint; }
    public Integer getCount() { return count; }
}
