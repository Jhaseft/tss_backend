package com.jhaseft.tss.dto;

/**
 * Resultado de interpretar una respuesta numérica de texto libre
 * (p.ej. "5 años", "6 meses", "Nada", "Gjvu"). No modifica el crudo: guarda el valor
 * original, el valor numérico derivado, la unidad detectada, el estado y el motivo del
 * descarte cuando corresponde. Ver {@link com.jhaseft.tss.service.parsing.NumericAnswerParser}.
 */
public class NumericParseResultDTO {

    /** Unidad detectada en el texto; NINGUNA = número sin unidad (se interpreta como años). */
    public enum Unit { ANO, MES, SEMANA, NINGUNA }

    /** VALIDO = número limpio en años; CONVERTIDO = se derivó de texto/unidad; INVALIDO = descartado. */
    public enum Status { VALIDO, CONVERTIDO, INVALIDO }

    /** Motivo del descarte (solo cuando status = INVALIDO). */
    public enum Reason { VACIO, SOLO_ESPACIOS, TEXTO_SIN_NUMERO, FUERA_DE_RANGO }

    private String rawValue;
    private Double numericValue;
    private Unit unit;
    private Status status;
    private Reason reason;

    public NumericParseResultDTO() {}

    public NumericParseResultDTO(String rawValue, Double numericValue, Unit unit,
                                 Status status, Reason reason) {
        this.rawValue = rawValue;
        this.numericValue = numericValue;
        this.unit = unit;
        this.status = status;
        this.reason = reason;
    }

    public boolean isValid() { return status != Status.INVALIDO; }

    public String getRawValue() { return rawValue; }
    public void setRawValue(String rawValue) { this.rawValue = rawValue; }

    public Double getNumericValue() { return numericValue; }
    public void setNumericValue(Double numericValue) { this.numericValue = numericValue; }

    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Reason getReason() { return reason; }
    public void setReason(Reason reason) { this.reason = reason; }
}
