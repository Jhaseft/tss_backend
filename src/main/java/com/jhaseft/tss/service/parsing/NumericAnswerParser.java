package com.jhaseft.tss.service.parsing;

import com.jhaseft.tss.dto.NumericParseResultDTO;
import com.jhaseft.tss.dto.NumericParseResultDTO.Reason;
import com.jhaseft.tss.dto.NumericParseResultDTO.Status;
import com.jhaseft.tss.dto.NumericParseResultDTO.Unit;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta respuestas numéricas escritas como texto libre en las encuestas
 * ("5 años", "6 meses", "Unos 3 meses", "1,5", "Nada", "Gjvu") sin tocar el valor crudo.
 *
 * Reglas (documentadas en el Informe de Calidad, punto 1):
 *  - trim + minúsculas, se quitan las tildes y se normalizan las variantes de unidad
 *    (anios/años/ano/anos → año; mes/meses; semana/semanas). Acepta coma o punto decimal.
 *  - año(s) → ×1, mes(es) → ÷12, semana(s) → ÷52. Un número SIN unidad se toma como años.
 *  - "nada", "ninguno/ninguna" y "0" → 0.
 *  - Sin número (y distinto de "nada") → INVALIDO/TEXTO_SIN_NUMERO; null → VACIO;
 *    solo espacios → SOLO_ESPACIOS; fuera del rango → FUERA_DE_RANGO.
 */
@Component
public class NumericAnswerParser {

    // Número con coma o punto decimal (la coma se convierte a punto antes de aplicarlo).
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    // Un crudo "limpio" es un número puro en años (sin letras ni unidad).
    private static final Pattern CLEAN_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    // Unidades (sobre texto ya sin tildes).
    private static final Pattern YEAR  = Pattern.compile("\\b(ano|anos|anio|anios)\\b");
    private static final Pattern MONTH = Pattern.compile("\\b(mes|meses)\\b");
    private static final Pattern WEEK  = Pattern.compile("\\b(semana|semanas)\\b");
    private static final Pattern NADA  = Pattern.compile("\\b(nada|ninguno|ninguna)\\b");

    /**
     * @param raw valor crudo tal como vino del archivo (puede ser null).
     * @param min límite inferior aceptado (inclusive).
     * @param max límite superior aceptado (inclusive).
     */
    public NumericParseResultDTO parse(String raw, double min, double max) {
        if (raw == null) return invalid(raw, Reason.VACIO);
        if (raw.isEmpty()) return invalid(raw, Reason.VACIO);
        if (raw.isBlank()) return invalid(raw, Reason.SOLO_ESPACIOS);

        String norm = normalize(raw);
        boolean nada = NADA.matcher(norm).find();

        Matcher m = NUMBER.matcher(norm.replace(',', '.'));
        if (!m.find()) {
            // "nada"/"ninguno" sin número → 0 (derivado de texto).
            if (nada) return ranged(raw, 0.0, Unit.NINGUNA, Status.CONVERTIDO, min, max);
            return invalid(raw, Reason.TEXTO_SIN_NUMERO);
        }

        double value = Double.parseDouble(m.group());
        Unit unit = Unit.NINGUNA;
        if (YEAR.matcher(norm).find()) {
            unit = Unit.ANO;                       // ×1
        } else if (MONTH.matcher(norm).find()) {
            unit = Unit.MES;  value /= 12.0;
        } else if (WEEK.matcher(norm).find()) {
            unit = Unit.SEMANA; value /= 52.0;
        }

        // Número puro en años (sin letras ni unidad) = VALIDO; cualquier derivación = CONVERTIDO.
        boolean clean = unit == Unit.NINGUNA
                && CLEAN_NUMBER.matcher(raw.trim().replace(',', '.')).matches();
        Status status = clean ? Status.VALIDO : Status.CONVERTIDO;

        return ranged(raw, value, unit, status, min, max);
    }

    private NumericParseResultDTO ranged(String raw, double value, Unit unit,
                                         Status status, double min, double max) {
        if (value < min || value > max)
            return new NumericParseResultDTO(raw, value, unit, Status.INVALIDO, Reason.FUERA_DE_RANGO);
        return new NumericParseResultDTO(raw, value, unit, status, null);
    }

    private NumericParseResultDTO invalid(String raw, Reason reason) {
        return new NumericParseResultDTO(raw, null, Unit.NINGUNA, Status.INVALIDO, reason);
    }

    private static String normalize(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.trim().replaceAll("\\s+", " ");
    }
}
