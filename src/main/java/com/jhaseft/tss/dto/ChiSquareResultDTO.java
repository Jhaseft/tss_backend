package com.jhaseft.tss.dto;

/**
 * Resultado estadístico de una prueba χ² de independencia sobre una tabla de contingencia
 * (Informe de Calidad, punto 7). No conoce las etiquetas: solo los números. Los produce
 * {@link com.jhaseft.tss.service.stats.ChiSquareService}.
 */
public class ChiSquareResultDTO {

    private long n;
    private double[][] expected;     // frecuencias esperadas E = (fila·col)/n
    private double chiSquare;
    private int degreesOfFreedom;
    private double pValue;           // p exacto (cola superior de la χ²)

    private double cramersV;
    private int cramersK;            // k = mín(filas, columnas)
    private String cramersVLevel;    // interpretación de Cohen según k − 1

    // Cochran
    private int lowExpectedCells;    // nº de celdas con E < 5
    private int totalCells;
    private double lowExpectedPct;   // % de celdas con E < 5
    private boolean anyExpectedBelow1;
    private boolean cochranOk;       // ≤ 20 % de celdas con E < 5 y ninguna < 1

    private Double fisherP;          // prueba exacta de Fisher bilateral (solo tablas 2×2)

    public long getN() { return n; }
    public void setN(long n) { this.n = n; }
    public double[][] getExpected() { return expected; }
    public void setExpected(double[][] expected) { this.expected = expected; }
    public double getChiSquare() { return chiSquare; }
    public void setChiSquare(double chiSquare) { this.chiSquare = chiSquare; }
    public int getDegreesOfFreedom() { return degreesOfFreedom; }
    public void setDegreesOfFreedom(int degreesOfFreedom) { this.degreesOfFreedom = degreesOfFreedom; }
    public double getPValue() { return pValue; }
    public void setPValue(double pValue) { this.pValue = pValue; }
    public double getCramersV() { return cramersV; }
    public void setCramersV(double cramersV) { this.cramersV = cramersV; }
    public int getCramersK() { return cramersK; }
    public void setCramersK(int cramersK) { this.cramersK = cramersK; }
    public String getCramersVLevel() { return cramersVLevel; }
    public void setCramersVLevel(String cramersVLevel) { this.cramersVLevel = cramersVLevel; }
    public int getLowExpectedCells() { return lowExpectedCells; }
    public void setLowExpectedCells(int lowExpectedCells) { this.lowExpectedCells = lowExpectedCells; }
    public int getTotalCells() { return totalCells; }
    public void setTotalCells(int totalCells) { this.totalCells = totalCells; }
    public double getLowExpectedPct() { return lowExpectedPct; }
    public void setLowExpectedPct(double lowExpectedPct) { this.lowExpectedPct = lowExpectedPct; }
    public boolean isAnyExpectedBelow1() { return anyExpectedBelow1; }
    public void setAnyExpectedBelow1(boolean anyExpectedBelow1) { this.anyExpectedBelow1 = anyExpectedBelow1; }
    public boolean isCochranOk() { return cochranOk; }
    public void setCochranOk(boolean cochranOk) { this.cochranOk = cochranOk; }
    public Double getFisherP() { return fisherP; }
    public void setFisherP(Double fisherP) { this.fisherP = fisherP; }
}
