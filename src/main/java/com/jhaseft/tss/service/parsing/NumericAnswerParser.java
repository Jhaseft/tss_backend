package com.jhaseft.tss.service.parsing;

import com.jhaseft.tss.dto.NumericParseResultDTO;
import com.jhaseft.tss.dto.NumericParseResultDTO.Reason;
import com.jhaseft.tss.dto.NumericParseResultDTO.Status;
import com.jhaseft.tss.dto.NumericParseResultDTO.Unit;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta respuestas numericas escritas como texto libre en las encuestas
 * ("5 anios", "6 meses", "Unos 3 meses", "1,5", "Dos anios", "Nada", "Gjvu") sin tocar el crudo.
 *
 * Reglas (Informe de Calidad, punto 1; criterio B del grupo):
 *  - trim + minusculas, se quitan las tildes, el espacio duro U+00A0 se trata como espacio
 *    normal y se normalizan las variantes de unidad (anios/anos/ano -> anio; mes/meses;
 *    semana/semanas). Acepta coma o punto decimal.
 *  - anio(s) -> x1, mes(es) -> /12, semana(s) -> /52. Un numero SIN unidad se toma como anios.
 *  - Numeros escritos con palabras del 0 al 45 ("dos", "veinte", "treinta y cinco") -> CONVERTIDO.
 *  - Cotas abiertas ("mas de", "menos de", "aprox", "casi", "+10", ">10") -> INVALIDO con motivo
 *    COTA_ABIERTA; se conserva el numero detectado en numericValue como referencia.
 *  - "nada", "ninguno/ninguna" y "0" -> 0.
 *  - Sin numero (y distinto de "nada") -> INVALIDO/TEXTO_SIN_NUMERO; null/"" -> VACIO;
 *    solo espacios -> SOLO_ESPACIOS; fuera del rango -> FUERA_DE_RANGO.
 */
@Component
public class NumericAnswerParser {

    // Numero con coma o punto decimal (la coma se convierte a punto antes de aplicarlo).
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    // Un crudo "limpio" es un numero puro en anios (sin letras ni unidad).
    private static final Pattern CLEAN_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    // Unidades (sobre texto ya sin tildes).
    private static final Pattern YEAR  = Pattern.compile("\\b(ano|anos|anio|anios)\\b");
    private static final Pattern MONTH = Pattern.compile("\\b(mes|meses)\\b");
    private static final Pattern WEEK  = Pattern.compile("\\b(semana|semanas)\\b");
    private static final Pattern NADA  = Pattern.compile("\\b(nada|ninguno|ninguna)\\b");

    // Cota abierta: no se conoce el valor exacto. Las formas simbolicas (+/</>) van pegadas al numero.
    private static final Pattern OPEN_BOUND =
            Pattern.compile("\\bmas de\\b|\\bmenos de\\b|\\baprox|\\bcasi\\b|[+<>]\\s*\\d");

    // Numeros escritos con palabras del 0 al 45 (sin tildes), ordenados por longitud desc para que
    // "treinta y cinco" se reconozca antes que "treinta". Clave = patron \bpalabra\b.
    private static final List<Map.Entry<Pattern, Double>> WORD_NUMBERS = buildWordNumbers();

    private static List<Map.Entry<Pattern, Double>> buildWordNumbers() {
        Map<String, Double> w = new java.util.HashMap<>();
        String[] ones = {"cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve"};
        for (int i = 0; i <= 9; i++) w.put(ones[i], (double) i);
        w.put("una", 1.0);
        String[] tens10 = {"diez", "once", "doce", "trece", "catorce", "quince",
                "dieciseis", "diecisiete", "dieciocho", "diecinueve"};
        for (int i = 0; i < tens10.length; i++) w.put(tens10[i], (double) (10 + i));
        w.put("veinte", 20.0);
        String[] twenties = {"veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco",
                "veintiseis", "veintisiete", "veintiocho", "veintinueve"};
        for (int i = 0; i < twenties.length; i++) w.put(twenties[i], (double) (21 + i));
        w.put("veintiuna", 21.0);
        w.put("treinta", 30.0);
        w.put("cuarenta", 40.0);
        for (int i = 1; i <= 9; i++) w.put("treinta y " + ones[i], (double) (30 + i));
        for (int i = 1; i <= 5; i++) w.put("cuarenta y " + ones[i], (double) (40 + i));

        List<Map.Entry<Pattern, Double>> out = new ArrayList<>();
        w.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .forEach(e -> out.add(new SimpleEntry<>(
                        Pattern.compile("\\b" + Pattern.quote(e.getKey()) + "\\b"), e.getValue())));
        return out;
    }

    /**
     * @param raw valor crudo tal como vino del archivo (puede ser null).
     * @param min limite inferior aceptado (inclusive).
     * @param max limite superior aceptado (inclusive).
     */
    public NumericParseResultDTO parse(String raw, double min, double max) {
        if (raw == null || raw.isEmpty()) return invalid(raw, Reason.VACIO, null);

        String norm = normalize(raw);
        if (norm.isBlank()) return invalid(raw, Reason.SOLO_ESPACIOS, null);

        // 1) Cota abierta: ambigua, se descarta pero se conserva el numero como referencia.
        if (OPEN_BOUND.matcher(norm).find())
            return invalid(raw, Reason.COTA_ABIERTA, detectNumber(norm));

        boolean nada = NADA.matcher(norm).find();

        // 2) Numero en digitos.
        Matcher m = NUMBER.matcher(norm.replace(',', '.'));
        if (m.find()) {
            Unit unit = unitOf(norm);
            double value = applyUnit(Double.parseDouble(m.group()), unit);
            boolean clean = unit == Unit.NINGUNA
                    && CLEAN_NUMBER.matcher(raw.trim().replace(',', '.')).matches();
            return ranged(raw, value, unit, clean ? Status.VALIDO : Status.CONVERTIDO, min, max);
        }

        // 3) Numero escrito con palabras (0-45) -> siempre conversion.
        Double word = wordNumber(norm);
        if (word != null) {
            Unit unit = unitOf(norm);
            return ranged(raw, applyUnit(word, unit), unit, Status.CONVERTIDO, min, max);
        }

        // 4) "nada"/"ninguno" sin numero -> 0 (derivado de texto).
        if (nada) return ranged(raw, 0.0, Unit.NINGUNA, Status.CONVERTIDO, min, max);

        // 5) Texto sin numero interpretable.
        return invalid(raw, Reason.TEXTO_SIN_NUMERO, null);
    }

    private Unit unitOf(String norm) {
        if (YEAR.matcher(norm).find())  return Unit.ANO;
        if (MONTH.matcher(norm).find()) return Unit.MES;
        if (WEEK.matcher(norm).find())  return Unit.SEMANA;
        return Unit.NINGUNA;
    }

    private double applyUnit(double value, Unit unit) {
        return switch (unit) {
            case MES    -> value / 12.0;
            case SEMANA -> value / 52.0;
            default     -> value;              // ANO o NINGUNA -> x1 (sin unidad = anios)
        };
    }

    /** Numero de referencia para una cota abierta: digito si lo hay, si no la palabra-numero. */
    private Double detectNumber(String norm) {
        Matcher m = NUMBER.matcher(norm.replace(',', '.'));
        if (m.find()) return Double.parseDouble(m.group());
        return wordNumber(norm);
    }

    private Double wordNumber(String norm) {
        for (Map.Entry<Pattern, Double> e : WORD_NUMBERS)
            if (e.getKey().matcher(norm).find()) return e.getValue();
        return null;
    }

    private NumericParseResultDTO ranged(String raw, double value, Unit unit,
                                         Status status, double min, double max) {
        if (value < min || value > max)
            return new NumericParseResultDTO(raw, value, unit, Status.INVALIDO, Reason.FUERA_DE_RANGO);
        return new NumericParseResultDTO(raw, value, unit, status, null);
    }

    private NumericParseResultDTO invalid(String raw, Reason reason, Double reference) {
        return new NumericParseResultDTO(raw, reference, Unit.NINGUNA, Status.INVALIDO, reason);
    }

    private static String normalize(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\x{00A0}", " ");   // espacio duro de Microsoft Forms -> espacio normal
        return n.trim().replaceAll("\\s+", " ");
    }
}
