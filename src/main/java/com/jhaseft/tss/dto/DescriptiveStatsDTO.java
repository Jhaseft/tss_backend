package com.jhaseft.tss.dto;

import java.util.List;

/**
 * Estadística descriptiva completa de una variable numérica (Informe de Calidad, punto 5).
 * Varianza y DE muestrales (n − 1). Cuartiles/percentiles por interpolación lineal
 * (equivalente a QUARTILE.INC / PERCENTILE.INC de Excel); el método se declara en
 * {@link #percentileMethod}. Asimetría y curtosis en su versión muestral (SKEW/KURT de Excel).
 */
public class DescriptiveStatsDTO {

    private int n;
    private double mean;
    private double median;
    private List<Double> modes;       // todas las modas (empates incluidos)
    private int modeFrequency;        // nº de veces que aparece cada moda

    private double variance;          // muestral (n − 1)
    private double stdDev;            // muestral
    private double cv;                // coeficiente de variación (%)
    private double standardError;     // EE = s/√n

    private double tValue;            // t de Student para IC 95 % (gl = n − 1)
    private double ciLow;             // IC 95 % de la media
    private double ciHigh;

    private double min;
    private double max;
    private double range;

    private double q1;
    private double q3;
    private double iqr;
    private double p5;
    private double p10;
    private double p90;
    private double p95;
    private double p99;
    private String percentileMethod = "INTERPOLACION_LINEAL (QUARTILE.INC)";

    // Vallas de Tukey
    private double lowerFence;         // Q1 − 1,5·RIC (valla moderada inferior)
    private double upperFence;         // Q3 + 1,5·RIC (valla moderada superior)
    private double lowerExtremeFence;  // Q1 − 3·RIC
    private double upperExtremeFence;  // Q3 + 3·RIC
    private List<Double> moderateOutliers;
    private List<Double> extremeOutliers;
    private double lowerWhisker;       // último dato dentro de la valla inferior
    private double upperWhisker;       // último dato dentro de la valla superior

    private double skewnessPearson;     // 3(x̄ − Me)/s
    private double skewnessCoefficient; // coeficiente de asimetría muestral (SKEW)
    private double kurtosis;            // curtosis (exceso) muestral (KURT)

    private double shapiroW;            // Shapiro-Wilk W
    private double shapiroP;            // p asociado

    // Getters / setters
    public int getN() { return n; }
    public void setN(int n) { this.n = n; }
    public double getMean() { return mean; }
    public void setMean(double mean) { this.mean = mean; }
    public double getMedian() { return median; }
    public void setMedian(double median) { this.median = median; }
    public List<Double> getModes() { return modes; }
    public void setModes(List<Double> modes) { this.modes = modes; }
    public int getModeFrequency() { return modeFrequency; }
    public void setModeFrequency(int modeFrequency) { this.modeFrequency = modeFrequency; }
    public double getVariance() { return variance; }
    public void setVariance(double variance) { this.variance = variance; }
    public double getStdDev() { return stdDev; }
    public void setStdDev(double stdDev) { this.stdDev = stdDev; }
    public double getCv() { return cv; }
    public void setCv(double cv) { this.cv = cv; }
    public double getStandardError() { return standardError; }
    public void setStandardError(double standardError) { this.standardError = standardError; }
    public double getTValue() { return tValue; }
    public void setTValue(double tValue) { this.tValue = tValue; }
    public double getCiLow() { return ciLow; }
    public void setCiLow(double ciLow) { this.ciLow = ciLow; }
    public double getCiHigh() { return ciHigh; }
    public void setCiHigh(double ciHigh) { this.ciHigh = ciHigh; }
    public double getMin() { return min; }
    public void setMin(double min) { this.min = min; }
    public double getMax() { return max; }
    public void setMax(double max) { this.max = max; }
    public double getRange() { return range; }
    public void setRange(double range) { this.range = range; }
    public double getQ1() { return q1; }
    public void setQ1(double q1) { this.q1 = q1; }
    public double getQ3() { return q3; }
    public void setQ3(double q3) { this.q3 = q3; }
    public double getIqr() { return iqr; }
    public void setIqr(double iqr) { this.iqr = iqr; }
    public double getP5() { return p5; }
    public void setP5(double p5) { this.p5 = p5; }
    public double getP10() { return p10; }
    public void setP10(double p10) { this.p10 = p10; }
    public double getP90() { return p90; }
    public void setP90(double p90) { this.p90 = p90; }
    public double getP95() { return p95; }
    public void setP95(double p95) { this.p95 = p95; }
    public double getP99() { return p99; }
    public void setP99(double p99) { this.p99 = p99; }
    public String getPercentileMethod() { return percentileMethod; }
    public void setPercentileMethod(String percentileMethod) { this.percentileMethod = percentileMethod; }
    public double getLowerFence() { return lowerFence; }
    public void setLowerFence(double lowerFence) { this.lowerFence = lowerFence; }
    public double getUpperFence() { return upperFence; }
    public void setUpperFence(double upperFence) { this.upperFence = upperFence; }
    public double getLowerExtremeFence() { return lowerExtremeFence; }
    public void setLowerExtremeFence(double lowerExtremeFence) { this.lowerExtremeFence = lowerExtremeFence; }
    public double getUpperExtremeFence() { return upperExtremeFence; }
    public void setUpperExtremeFence(double upperExtremeFence) { this.upperExtremeFence = upperExtremeFence; }
    public List<Double> getModerateOutliers() { return moderateOutliers; }
    public void setModerateOutliers(List<Double> moderateOutliers) { this.moderateOutliers = moderateOutliers; }
    public List<Double> getExtremeOutliers() { return extremeOutliers; }
    public void setExtremeOutliers(List<Double> extremeOutliers) { this.extremeOutliers = extremeOutliers; }
    public double getLowerWhisker() { return lowerWhisker; }
    public void setLowerWhisker(double lowerWhisker) { this.lowerWhisker = lowerWhisker; }
    public double getUpperWhisker() { return upperWhisker; }
    public void setUpperWhisker(double upperWhisker) { this.upperWhisker = upperWhisker; }
    public double getSkewnessPearson() { return skewnessPearson; }
    public void setSkewnessPearson(double skewnessPearson) { this.skewnessPearson = skewnessPearson; }
    public double getSkewnessCoefficient() { return skewnessCoefficient; }
    public void setSkewnessCoefficient(double skewnessCoefficient) { this.skewnessCoefficient = skewnessCoefficient; }
    public double getKurtosis() { return kurtosis; }
    public void setKurtosis(double kurtosis) { this.kurtosis = kurtosis; }
    public double getShapiroW() { return shapiroW; }
    public void setShapiroW(double shapiroW) { this.shapiroW = shapiroW; }
    public double getShapiroP() { return shapiroP; }
    public void setShapiroP(double shapiroP) { this.shapiroP = shapiroP; }
}
