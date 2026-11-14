package com.jhaseft.tss.dto;

import java.util.List;
import java.util.Map;

/**
 * Petición del análisis multivariable: dos cualitativas (X, Y) y una cuantitativa (Z, pasa por el
 * parser numérico). Opcionalmente, un reagrupamiento de categorías por eje (mapa valor→grupo) y un
 * filtro por otra pregunta (solo registros cuyo valor normalizado esté en {@code filterValues}).
 */
public class MultivariateRequestDTO {

    private Long questionIdX;
    private Long questionIdY;
    private Long questionIdZ;

    private Map<String, String> groupingX;   // valor normalizado → etiqueta de grupo (opcional)
    private Map<String, String> groupingY;

    private Long filterQuestionId;            // filtro opcional
    private List<String> filterValues;        // valores normalizados admitidos

    public Long getQuestionIdX() { return questionIdX; }
    public void setQuestionIdX(Long questionIdX) { this.questionIdX = questionIdX; }
    public Long getQuestionIdY() { return questionIdY; }
    public void setQuestionIdY(Long questionIdY) { this.questionIdY = questionIdY; }
    public Long getQuestionIdZ() { return questionIdZ; }
    public void setQuestionIdZ(Long questionIdZ) { this.questionIdZ = questionIdZ; }
    public Map<String, String> getGroupingX() { return groupingX; }
    public void setGroupingX(Map<String, String> groupingX) { this.groupingX = groupingX; }
    public Map<String, String> getGroupingY() { return groupingY; }
    public void setGroupingY(Map<String, String> groupingY) { this.groupingY = groupingY; }
    public Long getFilterQuestionId() { return filterQuestionId; }
    public void setFilterQuestionId(Long filterQuestionId) { this.filterQuestionId = filterQuestionId; }
    public List<String> getFilterValues() { return filterValues; }
    public void setFilterValues(List<String> filterValues) { this.filterValues = filterValues; }
}
