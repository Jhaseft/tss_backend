package com.jhaseft.tss.dto;

import java.util.List;

/**
 * Comparación descriptiva de dos encuestas independientes (p. ej. titulados vs empleadores).
 * NO es un cruce ni un χ²: son dos muestras distintas sin unidad de observación común. Para cada
 * categoría/tema se informa el % de cada encuesta con su IC de Wilson 95 % (referencial, muestreo no
 * probabilístico). El campo {@link #disclaimer} deja constancia de la limitación metodológica.
 */
public class CompareResultDTO {

    /** Una categoría/tema con sus dos proporciones (A y B) e intervalos de Wilson. */
    public static class Row {
        public String label;
        public long countA;
        public double pctA;
        public double ciLowA;
        public double ciHighA;
        public long countB;
        public double pctB;
        public double ciLowB;
        public double ciHighB;
        public double gap;   // pctA − pctB (en puntos porcentuales)
    }

    private String mode;

    private Long surveyIdA;
    private String surveyNameA;
    private Long questionIdA;
    private String questionTextA;
    private int nA;

    private Long surveyIdB;
    private String surveyNameB;
    private Long questionIdB;
    private String questionTextB;
    private int nB;

    private List<Row> rows;
    private String disclaimer;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Long getSurveyIdA() { return surveyIdA; }
    public void setSurveyIdA(Long surveyIdA) { this.surveyIdA = surveyIdA; }
    public String getSurveyNameA() { return surveyNameA; }
    public void setSurveyNameA(String surveyNameA) { this.surveyNameA = surveyNameA; }
    public Long getQuestionIdA() { return questionIdA; }
    public void setQuestionIdA(Long questionIdA) { this.questionIdA = questionIdA; }
    public String getQuestionTextA() { return questionTextA; }
    public void setQuestionTextA(String questionTextA) { this.questionTextA = questionTextA; }
    public int getNA() { return nA; }
    public void setNA(int nA) { this.nA = nA; }
    public Long getSurveyIdB() { return surveyIdB; }
    public void setSurveyIdB(Long surveyIdB) { this.surveyIdB = surveyIdB; }
    public String getSurveyNameB() { return surveyNameB; }
    public void setSurveyNameB(String surveyNameB) { this.surveyNameB = surveyNameB; }
    public Long getQuestionIdB() { return questionIdB; }
    public void setQuestionIdB(Long questionIdB) { this.questionIdB = questionIdB; }
    public String getQuestionTextB() { return questionTextB; }
    public void setQuestionTextB(String questionTextB) { this.questionTextB = questionTextB; }
    public int getNB() { return nB; }
    public void setNB(int nB) { this.nB = nB; }
    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows; }
    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }
}
