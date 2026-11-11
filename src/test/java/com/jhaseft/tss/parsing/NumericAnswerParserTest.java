package com.jhaseft.tss.parsing;

import com.jhaseft.tss.dto.NumericParseResultDTO;
import com.jhaseft.tss.dto.NumericParseResultDTO.Reason;
import com.jhaseft.tss.dto.NumericParseResultDTO.Status;
import com.jhaseft.tss.dto.NumericParseResultDTO.Unit;
import com.jhaseft.tss.service.parsing.NumericAnswerParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Valores de referencia del Informe de Calidad, punto 1 (rango vida profesional 0–45). */
class NumericAnswerParserTest {

    private final NumericAnswerParser parser = new NumericAnswerParser();

    private NumericParseResultDTO parse(String raw) {
        return parser.parse(raw, 0, 45);
    }

    @Test
    void convierteAniosConUnidad() {
        NumericParseResultDTO r = parse("5 años");
        assertEquals(5.0, r.getNumericValue(), 1e-9);
        assertEquals(Unit.ANO, r.getUnit());
        assertEquals(Status.CONVERTIDO, r.getStatus());
    }

    @Test
    void convierteAniosSinTilde() {
        assertEquals(2.0, parse("2 anios").getNumericValue(), 1e-9);
    }

    @Test
    void aceptaDecimalConComa() {
        NumericParseResultDTO r = parse("1,5");
        assertEquals(1.5, r.getNumericValue(), 1e-9);
        assertEquals(Status.VALIDO, r.getStatus());
        assertEquals(Unit.NINGUNA, r.getUnit());
    }

    @Test
    void convierteMesesADivididoEntreDoce() {
        NumericParseResultDTO r = parse("6 meses");
        assertEquals(0.5, r.getNumericValue(), 1e-9);
        assertEquals(Unit.MES, r.getUnit());
    }

    @Test
    void extraeNumeroEntreTextoYMeses() {
        assertEquals(0.25, parse("Unos 3 meses").getNumericValue(), 1e-9); // 3/12
    }

    @Test
    void nadaEsCero() {
        NumericParseResultDTO r = parse("Nada");
        assertEquals(0.0, r.getNumericValue(), 1e-9);
        assertTrue(r.isValid());
    }

    @Test
    void vacioEsInvalidoVacio() {
        NumericParseResultDTO r = parse("");
        assertEquals(Status.INVALIDO, r.getStatus());
        assertEquals(Reason.VACIO, r.getReason());
    }

    @Test
    void nullEsInvalidoVacio() {
        assertEquals(Reason.VACIO, parse(null).getReason());
    }

    @Test
    void soloEspaciosEsInvalido() {
        NumericParseResultDTO r = parse(" ");
        assertEquals(Status.INVALIDO, r.getStatus());
        assertEquals(Reason.SOLO_ESPACIOS, r.getReason());
    }

    @Test
    void textoSinNumeroEsInvalido() {
        assertEquals(Reason.TEXTO_SIN_NUMERO, parse("Gjvu").getReason());
    }

    @Test
    void fueraDeRangoEsInvalido() {
        NumericParseResultDTO r = parse("60");
        assertEquals(Status.INVALIDO, r.getStatus());
        assertEquals(Reason.FUERA_DE_RANGO, r.getReason());
        assertEquals(60.0, r.getNumericValue(), 1e-9); // conserva el número
    }

    @Test
    void semanasADivididoEntre52() {
        assertEquals(2.0 / 52.0, parse("2 semanas").getNumericValue(), 1e-9);
    }

    @Test
    void ceroEsValido() {
        NumericParseResultDTO r = parse("0");
        assertEquals(0.0, r.getNumericValue(), 1e-9);
        assertEquals(Status.VALIDO, r.getStatus());
    }

    @Test
    void masDeDiezAniosExtraeDiez() {
        assertEquals(10.0, parse("Más de 10 años").getNumericValue(), 1e-9);
    }
}
