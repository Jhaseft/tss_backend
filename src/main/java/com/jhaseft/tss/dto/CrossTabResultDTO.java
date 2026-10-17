package com.jhaseft.tss.dto;

import java.util.List;

public class CrossTabResultDTO {
    private Long questionId1;
    private String questionText1;
    private Long questionId2;
    private String questionText2;

    private List<String> rowLabels;
    private List<String> colLabels;
    private long[][] observed;
    private double[][] rowPct;
    private long[] rowTotals;
    private long[] colTotals;
    private long n;

    private double chiSquare;
    private int degreesOfFreedom;
    private double pValue;
    private double cramersV;
    private boolean significant;
    private String warning;

    public Long getQuestionId1() { return questionId1; }
    public void setQuestionId1(Long questionId1) { this.questionId1 = questionId1; }

    public String getQuestionText1() { return questionText1; }
    public void setQuestionText1(String questionText1) { this.questionText1 = questionText1; }

    public Long getQuestionId2() { return questionId2; }
    public void setQuestionId2(Long questionId2) { this.questionId2 = questionId2; }

    public String getQuestionText2() { return questionText2; }
    public void setQuestionText2(String questionText2) { this.questionText2 = questionText2; }

    public List<String> getRowLabels() { return rowLabels; }
    public void setRowLabels(List<String> rowLabels) { this.rowLabels = rowLabels; }

    public List<String> getColLabels() { return colLabels; }
    public void setColLabels(List<String> colLabels) { this.colLabels = colLabels; }

    public long[][] getObserved() { return observed; }
    public void setObserved(long[][] observed) { this.observed = observed; }

    public double[][] getRowPct() { return rowPct; }
    public void setRowPct(double[][] rowPct) { this.rowPct = rowPct; }

    public long[] getRowTotals() { return rowTotals; }
    public void setRowTotals(long[] rowTotals) { this.rowTotals = rowTotals; }

    public long[] getColTotals() { return colTotals; }
    public void setColTotals(long[] colTotals) { this.colTotals = colTotals; }

    public long getN() { return n; }
    public void setN(long n) { this.n = n; }

    public double getChiSquare() { return chiSquare; }
    public void setChiSquare(double chiSquare) { this.chiSquare = chiSquare; }

    public int getDegreesOfFreedom() { return degreesOfFreedom; }
    public void setDegreesOfFreedom(int degreesOfFreedom) { this.degreesOfFreedom = degreesOfFreedom; }

    public double getPValue() { return pValue; }
    public void setPValue(double pValue) { this.pValue = pValue; }

    public double getCramersV() { return cramersV; }
    public void setCramersV(double cramersV) { this.cramersV = cramersV; }

    public boolean isSignificant() { return significant; }
    public void setSignificant(boolean significant) { this.significant = significant; }

    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
}
