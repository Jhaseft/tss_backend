package com.jhaseft.tss.dto;

/**
 * Petición de comparación entre dos encuestas: una pregunta de cada una y el modo.
 * CATEGORIES compara las distribuciones por categoría (alineadas por etiqueta); KEYWORDS compara la
 * prevalencia de temas en texto libre (vocabulario común: IA, Ciberseguridad, Datos, …).
 */
public class CompareRequestDTO {

    private Long questionIdA;
    private Long questionIdB;
    private String mode = "CATEGORIES";   // CATEGORIES | KEYWORDS

    public Long getQuestionIdA() { return questionIdA; }
    public void setQuestionIdA(Long questionIdA) { this.questionIdA = questionIdA; }
    public Long getQuestionIdB() { return questionIdB; }
    public void setQuestionIdB(Long questionIdB) { this.questionIdB = questionIdB; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
