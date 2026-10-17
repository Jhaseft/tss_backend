package com.jhaseft.tss.dto;

import java.util.List;

public class SimulationResultDTO {
    private String questionText;
    private String questionType;
    private String simulationType;
    private Integer iterations;
    private Integer n;

    // For BOOTSTRAP_MEAN
    private Double estimate;
    private Double ciLow95;
    private Double ciHigh95;
    private Double stdError;
    private List<HistogramBinDTO> histogram;

    // For BOOTSTRAP_PROPORTION (one entry per option)
    private List<OptionBootstrapDTO> optionResults;

    // ── nested DTO ──────────────────────────────────────────────────────────
    public static class OptionBootstrapDTO {
        private String label;
        private Double estimate;
        private Double ciLow95;
        private Double ciHigh95;
        private List<HistogramBinDTO> histogram;

        public OptionBootstrapDTO(String label, Double estimate, Double ciLow95, Double ciHigh95,
                                   List<HistogramBinDTO> histogram) {
            this.label = label; this.estimate = estimate;
            this.ciLow95 = ciLow95; this.ciHigh95 = ciHigh95;
            this.histogram = histogram;
        }

        public String getLabel() { return label; }
        public Double getEstimate() { return estimate; }
        public Double getCiLow95() { return ciLow95; }
        public Double getCiHigh95() { return ciHigh95; }
        public List<HistogramBinDTO> getHistogram() { return histogram; }
    }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getSimulationType() { return simulationType; }
    public void setSimulationType(String simulationType) { this.simulationType = simulationType; }

    public Integer getIterations() { return iterations; }
    public void setIterations(Integer iterations) { this.iterations = iterations; }

    public Integer getN() { return n; }
    public void setN(Integer n) { this.n = n; }

    public Double getEstimate() { return estimate; }
    public void setEstimate(Double estimate) { this.estimate = estimate; }

    public Double getCiLow95() { return ciLow95; }
    public void setCiLow95(Double ciLow95) { this.ciLow95 = ciLow95; }

    public Double getCiHigh95() { return ciHigh95; }
    public void setCiHigh95(Double ciHigh95) { this.ciHigh95 = ciHigh95; }

    public Double getStdError() { return stdError; }
    public void setStdError(Double stdError) { this.stdError = stdError; }

    public List<HistogramBinDTO> getHistogram() { return histogram; }
    public void setHistogram(List<HistogramBinDTO> histogram) { this.histogram = histogram; }

    public List<OptionBootstrapDTO> getOptionResults() { return optionResults; }
    public void setOptionResults(List<OptionBootstrapDTO> optionResults) { this.optionResults = optionResults; }
}
