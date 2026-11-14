package com.jhaseft.tss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Resultado del análisis exploratorio multivariable (método del docente): dos variables
 * cualitativas X e Y más una cuantitativa Z. Para cada combinación X×Y se informa la frecuencia
 * y los estadísticos de Z; también los marginales por X y por Y, el total y los puntos
 * individuales para el gráfico de dispersión categorizada.
 */
public class MultivariateResultDTO {

    /** Celda X×Y: n, % y estadísticos de Z. */
    public static class Cell {
        public String x;
        public String y;
        public int n;
        public double pct;
        public Double meanZ;
        public Double medianZ;
        public Double stdDevZ;
        public Double minZ;
        public Double maxZ;
    }

    /** Marginal (por X, por Y o total). */
    public static class Marginal {
        public String label;
        public int n;
        public double pct;
        public Double meanZ;
        public Double medianZ;
        public Double stdDevZ;
    }

    /** Punto individual para la dispersión categorizada. */
    public static class Point {
        public String x;
        public String y;
        public double z;
    }

    private Long questionIdX;
    private String questionTextX;
    private Long questionIdY;
    private String questionTextY;
    private Long questionIdZ;
    private String questionTextZ;

    private List<String> xLabels;   // ordenadas (ordinal: escala; nominal: frecuencia)
    private List<String> yLabels;

    private List<Cell> cells;
    private List<Marginal> marginalX;
    private List<Marginal> marginalY;
    private Marginal total;
    private List<Point> points;

    private int usedN;                         // registros con X, Y y Z válidos
    private Map<String, Integer> excluded;     // excluidos por motivo

    public Long getQuestionIdX() { return questionIdX; }
    public void setQuestionIdX(Long questionIdX) { this.questionIdX = questionIdX; }
    public String getQuestionTextX() { return questionTextX; }
    public void setQuestionTextX(String questionTextX) { this.questionTextX = questionTextX; }
    public Long getQuestionIdY() { return questionIdY; }
    public void setQuestionIdY(Long questionIdY) { this.questionIdY = questionIdY; }
    public String getQuestionTextY() { return questionTextY; }
    public void setQuestionTextY(String questionTextY) { this.questionTextY = questionTextY; }
    public Long getQuestionIdZ() { return questionIdZ; }
    public void setQuestionIdZ(Long questionIdZ) { this.questionIdZ = questionIdZ; }
    public String getQuestionTextZ() { return questionTextZ; }
    public void setQuestionTextZ(String questionTextZ) { this.questionTextZ = questionTextZ; }
    @JsonProperty("xLabels")
    public List<String> getXLabels() { return xLabels; }
    public void setXLabels(List<String> xLabels) { this.xLabels = xLabels; }
    @JsonProperty("yLabels")
    public List<String> getYLabels() { return yLabels; }
    public void setYLabels(List<String> yLabels) { this.yLabels = yLabels; }
    public List<Cell> getCells() { return cells; }
    public void setCells(List<Cell> cells) { this.cells = cells; }
    public List<Marginal> getMarginalX() { return marginalX; }
    public void setMarginalX(List<Marginal> marginalX) { this.marginalX = marginalX; }
    public List<Marginal> getMarginalY() { return marginalY; }
    public void setMarginalY(List<Marginal> marginalY) { this.marginalY = marginalY; }
    public Marginal getTotal() { return total; }
    public void setTotal(Marginal total) { this.total = total; }
    public List<Point> getPoints() { return points; }
    public void setPoints(List<Point> points) { this.points = points; }
    public int getUsedN() { return usedN; }
    public void setUsedN(int usedN) { this.usedN = usedN; }
    public Map<String, Integer> getExcluded() { return excluded; }
    public void setExcluded(Map<String, Integer> excluded) { this.excluded = excluded; }
}
