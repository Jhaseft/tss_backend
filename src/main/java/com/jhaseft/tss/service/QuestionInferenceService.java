package com.jhaseft.tss.service;

import com.jhaseft.tss.model.QuestionType;
import com.jhaseft.tss.model.SurveyOrigin;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class QuestionInferenceService {

    // ── Escalas Likert conocidas ─────────────────────────────────────────────

    private static final Map<String, Map<String, Integer>> LIKERT_SCALES = new LinkedHashMap<>();

    static {
        Map<String, Integer> acuerdo4 = new LinkedHashMap<>();
        acuerdo4.put("totalmente en desacuerdo", 1);
        acuerdo4.put("en desacuerdo", 2);
        acuerdo4.put("de acuerdo", 3);
        acuerdo4.put("totalmente de acuerdo", 4);
        LIKERT_SCALES.put("Acuerdo 4 puntos", acuerdo4);

        Map<String, Integer> satisfaccion3 = new LinkedHashMap<>();
        satisfaccion3.put("insatisfecho", 1);
        satisfaccion3.put("algo satisfecho", 2);
        satisfaccion3.put("satisfecho", 3);
        LIKERT_SCALES.put("Satisfacción 3 puntos", satisfaccion3);

        Map<String, Integer> importancia4 = new LinkedHashMap<>();
        importancia4.put("no es importante", 1);
        importancia4.put("poco importante", 2);
        importancia4.put("importante", 3);
        importancia4.put("muy importante", 4);
        LIKERT_SCALES.put("Importancia 4 puntos", importancia4);

        Map<String, Integer> empleadores = new LinkedHashMap<>();
        empleadores.put("totalmente desacuerdo", 1);
        empleadores.put("parcialmente de acuerdo", 2);
        empleadores.put("totalmente de acuerdo", 3);
        // "no sabe" → null (sin número)
        LIKERT_SCALES.put("Empleadores 3 puntos", empleadores);
    }

    // Columnas de metadatos por origen (normalizado a minúsculas, sin tildes)
    private static final Set<String> MS_METADATA = Set.of(
            "id", "hora de inicio", "hora de finalizacion",
            "correo electronico", "nombre",
            "total de puntos", "comentarios del cuestionario",
            "hora de la ultima modificacion"
    );

    private static final Set<String> GOOGLE_METADATA = Set.of(
            "marca temporal", "correo", "direccion de correo electronico",
            "timestamp", "email address"
    );

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[+]?[\\d\\s()\\-]{7,20}$");
    private static final Pattern URL_PATTERN =
            Pattern.compile("^https?://.*|^www\\..*|^linkedin\\..*");
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("^-?\\d+(\\.\\d+)?$");

    // ── API pública ──────────────────────────────────────────────────────────

    public List<InferredQuestion> inferAll(
            List<String> allHeaders,
            List<Map<String, String>> rows,
            SurveyOrigin origin) {

        List<String> questionHeaders = filterMetadata(allHeaders, origin);
        List<InferredQuestion> result = new ArrayList<>();
        int pos = 0;

        // Pre-detectar grupos de matriz Likert (Google Forms: encabezados en [])
        Map<String, List<String>> matrixGroups = detectMatrixGroups(questionHeaders);

        Set<String> processedAsMatrix = new HashSet<>();

        for (String header : questionHeaders) {
            if (processedAsMatrix.contains(header)) continue;

            String matrixKey = findMatrixKey(header, matrixGroups);
            if (matrixKey != null) {
                // Un InferredQuestion por ítem para que StatsService pueda construir filas individuales
                List<String> items = matrixGroups.get(matrixKey);
                for (String itemHeader : items) {
                    result.add(buildMatrixLikertItem(matrixKey, itemHeader, pos++));
                }
                processedAsMatrix.addAll(items);
            } else {
                result.add(infer(header, rows, origin, pos++));
            }
        }

        // Post-proceso: agrupar LIKERT consecutivos con la misma escala en MATRIX_LIKERT
        // Solo para MS Forms / Genérico — Google Forms ya usa la detección por [corchetes]
        if (origin != SurveyOrigin.GOOGLE_FORMS) {
            groupConsecutiveLikerts(result);
        }

        return result;
    }

    // ── Agrupación post-proceso de LIKERT consecutivos (MS Forms) ────────────

    private void groupConsecutiveLikerts(List<InferredQuestion> questions) {
        int n = questions.size();
        Map<String, Integer> baseCounts = new HashMap<>();
        int i = 0;
        while (i < n) {
            InferredQuestion q = questions.get(i);
            if (q.getType() != QuestionType.LIKERT || q.getLikertScaleName() == null) {
                i++;
                continue;
            }
            // Buscar hasta dónde llega el run de LIKERT con la misma escala
            String scaleName = q.getLikertScaleName();
            int j = i + 1;
            while (j < n
                    && questions.get(j).getType() == QuestionType.LIKERT
                    && scaleName.equals(questions.get(j).getLikertScaleName())) {
                j++;
            }
            // Solo agrupar si hay ≥2 preguntas consecutivas
            if (j - i >= 2) {
                String baseName = scaleName2GroupName(scaleName);
                int cnt = baseCounts.merge(baseName, 1, Integer::sum);
                String groupName = cnt == 1 ? baseName : baseName + " " + cnt;
                for (int k = i; k < j; k++) {
                    InferredQuestion qi = questions.get(k);
                    qi.setType(QuestionType.MATRIX_LIKERT);
                    qi.setMatrixGroupName(groupName);
                }
            }
            i = j;
        }
    }

    private String scaleName2GroupName(String scaleName) {
        if (scaleName == null) return "Bloque Likert";
        String s = scaleName.toLowerCase();
        if (s.contains("acuerdo"))   return "Nivel de acuerdo";
        if (s.contains("satisfac"))  return "Nivel de satisfacción";
        if (s.contains("importan"))  return "Nivel de importancia";
        if (s.contains("empleador")) return "Valoración empleadores";
        return "Bloque Likert";
    }

    // ── Filtrado de metadatos ────────────────────────────────────────────────

    public List<String> filterMetadata(List<String> headers, SurveyOrigin origin) {
        Set<String> metaSet = origin == SurveyOrigin.GOOGLE_FORMS ? GOOGLE_METADATA : MS_METADATA;

        return headers.stream()
                .filter(h -> {
                    String norm = normalize(h);
                    if (metaSet.contains(norm)) return false;
                    // MS Forms: columnas Puntos: y Comentarios:
                    if (norm.startsWith("puntos:") || norm.startsWith("comentarios:")) return false;
                    // Columnas de nombre de empresa / contacto interno (datos personales se guardan pero no filtran aquí)
                    return true;
                })
                .collect(Collectors.toList());
    }

    public String getTimestampColumn(List<String> headers, SurveyOrigin origin) {
        for (String h : headers) {
            String n = normalize(h);
            if (origin == SurveyOrigin.GOOGLE_FORMS && n.equals("marca temporal")) return h;
            if (origin == SurveyOrigin.MS_FORMS && n.equals("hora de finalizacion")) return h;
        }
        return null;
    }

    public SurveyOrigin detectOrigin(List<String> headers) {
        for (String h : headers) {
            String n = normalize(h);
            if (n.equals("marca temporal")) return SurveyOrigin.GOOGLE_FORMS;
            if (n.equals("hora de inicio") || n.equals("hora de finalizacion")) return SurveyOrigin.MS_FORMS;
        }
        return SurveyOrigin.GENERIC;
    }

    // ── Inferencia individual ────────────────────────────────────────────────

    private InferredQuestion infer(String header, List<Map<String, String>> rows,
                                   SurveyOrigin origin, int position) {
        String headerNorm = normalize(header);
        List<String> values = collectNonEmpty(header, rows);
        long distinct = distinctCount(values);
        double distinctRatio = values.isEmpty() ? 1.0 : (double) distinct / values.size();

        // 1. Datos personales: email
        if (isEmailHeader(headerNorm) || isEmail(values)) {
            return build(header, QuestionType.EMAIL, position, List.of(), true);
        }
        // 2. Datos personales: teléfono
        if (isPhoneHeader(headerNorm) || isPhone(values)) {
            return build(header, QuestionType.PHONE, position, List.of(), true);
        }
        // 3. Datos personales: URL/LinkedIn
        if (isUrlHeader(headerNorm) || isUrl(values)) {
            return build(header, QuestionType.URL, position, List.of(), true);
        }
        // 4. Selección múltiple
        if (isMultipleChoice(header, values, origin)) {
            List<OptionDef> opts = extractMultipleOptions(values, origin);
            return build(header, QuestionType.MULTIPLE_CHOICE, position, opts, false);
        }
        // 5. Numérica
        if (isNumeric(values)) {
            return build(header, QuestionType.NUMERIC, position, List.of(), false);
        }
        // 6. Escala Likert individual
        String matchedScale = matchLikert(values);
        if (matchedScale != null) {
            List<OptionDef> opts = buildLikertOptions(matchedScale);
            InferredQuestion q = build(header, QuestionType.LIKERT, position, opts, false);
            q.setLikertScaleName(matchedScale);
            return q;
        }
        // 7. Header sugiere texto libre — solo si además hay alta variedad de respuestas
        // (evita falsos positivos en preguntas que mencionan "nombre/descripción" en su texto)
        if (isOpenTextHeader(headerNorm) && distinctRatio > 0.70) {
            double avgLen7 = values.stream().mapToInt(String::length).average().orElse(0);
            return build(header, avgLen7 > 80 ? QuestionType.TEXT_LONG : QuestionType.TEXT_SHORT,
                    position, List.of(), false);
        }
        // 8. Alta unicidad → texto libre (umbral 0.90 para no confundir selección con muchas opciones)
        if (values.size() >= 5 && distinct > 0 && distinctRatio > 0.90) {
            double avgLen8 = values.stream().mapToInt(String::length).average().orElse(0);
            return build(header, avgLen8 > 80 ? QuestionType.TEXT_LONG : QuestionType.TEXT_SHORT,
                    position, List.of(), false);
        }
        // 9. Opción única (pocos valores distintos y baja unicidad)
        if (distinct > 0 && distinct <= 15 && values.size() >= 1) {
            List<OptionDef> opts = buildNominalOptions(values);
            return build(header, QuestionType.SINGLE_NOMINAL, position, opts, false);
        }
        // 10. Texto largo / corto como fallback
        double avgLen = values.stream().mapToInt(String::length).average().orElse(0);
        QuestionType textType = avgLen > 80 ? QuestionType.TEXT_LONG : QuestionType.TEXT_SHORT;
        return build(header, textType, position, List.of(), false);
    }

    // ── Detección de Matriz Likert (Google Forms: [Ítem]) ───────────────────

    private Map<String, List<String>> detectMatrixGroups(List<String> headers) {
        // Un encabezado entre corchetes = ítem de una matriz Likert en Google Forms
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String h : headers) {
            String trimmed = h.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                // Usamos el literal "Formación y perfil profesional" como grupo
                groups.computeIfAbsent("Formación y perfil profesional", k -> new ArrayList<>()).add(h);
            }
        }
        return groups;
    }

    private String findMatrixKey(String header, Map<String, List<String>> groups) {
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            if (e.getValue().contains(header)) return e.getKey();
        }
        return null;
    }

    private InferredQuestion buildMatrixLikertItem(String groupName, String itemHeader, int position) {
        List<OptionDef> likertOpts = buildLikertOptions("Empleadores 3 puntos");
        InferredQuestion q = new InferredQuestion();
        q.setColumnName(itemHeader);
        q.setText(cleanHeaderText(itemHeader));
        q.setType(QuestionType.MATRIX_LIKERT);
        q.setPosition(position);
        q.setPersonalData(false);
        q.setOptions(likertOpts);
        q.setLikertScaleName("Empleadores 3 puntos");
        q.setMatrixGroupName(groupName);
        return q;
    }

    // ── Helpers de detección ─────────────────────────────────────────────────

    private boolean isOpenTextHeader(String n) {
        // Solo palabras que identifican el CAMPO como texto libre, no contexto de la pregunta.
        // "organizacion", "empresa", "institucion", "indique" se omiten porque aparecen
        // frecuentemente en el texto de preguntas de selección y generan falsos positivos.
        return n.contains("nombre") || n.contains("descripcion") || n.contains("describa")
                || n.contains("especifique") || n.contains("especifica") || n.contains("comentario")
                || n.contains("observacion") || n.contains("sugerencia") || n.contains("otro")
                || n.contains("mencione") || n.contains("direccion") || n.contains("ciudad")
                || n.contains("pais");
    }

    private boolean isEmailHeader(String n) {
        return n.contains("email") || n.contains("correo") || n.contains("e-mail");
    }

    private boolean isPhoneHeader(String n) {
        return n.contains("telefono") || n.contains("celular") || n.contains("whatsapp") || n.contains("movil");
    }

    private boolean isUrlHeader(String n) {
        return n.contains("linkedin") || n.contains("link") || n.contains("url") || n.contains("perfil");
    }

    private boolean isEmail(List<String> values) {
        if (values.isEmpty()) return false;
        return values.stream().filter(v -> !v.isBlank()).allMatch(v -> EMAIL_PATTERN.matcher(v).matches());
    }

    private boolean isPhone(List<String> values) {
        if (values.isEmpty()) return false;
        return values.stream().filter(v -> !v.isBlank()).allMatch(v -> PHONE_PATTERN.matcher(v).matches());
    }

    private boolean isUrl(List<String> values) {
        if (values.isEmpty()) return false;
        return values.stream().filter(v -> !v.isBlank()).allMatch(v -> URL_PATTERN.matcher(v.toLowerCase()).find());
    }

    private boolean isNumeric(List<String> values) {
        if (values.isEmpty()) return false;
        return values.stream().filter(v -> !v.isBlank()).allMatch(v -> NUMBER_PATTERN.matcher(v).matches());
    }

    private boolean isMultipleChoice(String header, List<String> values, SurveyOrigin origin) {
        String headerNorm = normalize(header);
        // Hint explícito en el encabezado (fiable para Google Forms y genérico)
        if (headerNorm.contains("puede seleccionar") || headerNorm.contains("seleccione") || headerNorm.contains("marque")) {
            return true;
        }
        // MS Forms: algún valor contiene ';' (separador interno de múltiple)
        if (origin == SurveyOrigin.MS_FORMS) {
            return values.stream().anyMatch(v -> v.contains(";") && v.split(";").length > 1);
        }
        // Google Forms: detección por valores solo si algún fragmento del split aparece
        // como valor completo en otra fila (descarta opciones simples que contienen comas).
        if (origin == SurveyOrigin.GOOGLE_FORMS) {
            Set<String> wholeVals = new HashSet<>(values);
            return values.stream().anyMatch(v -> {
                List<String> parts = splitOutsideParens(v);
                if (parts.size() <= 1) return false;
                return parts.stream().anyMatch(p -> wholeVals.contains(p.trim()));
            });
        }
        return false;
    }

    private String matchLikert(List<String> values) {
        if (values.isEmpty()) return null;
        Set<String> normValues = values.stream()
                .filter(v -> !v.isBlank())
                .map(this::normalize)
                .collect(Collectors.toSet());

        for (Map.Entry<String, Map<String, Integer>> entry : LIKERT_SCALES.entrySet()) {
            Set<String> scaleKeys = entry.getValue().keySet();
            long matches = normValues.stream().filter(scaleKeys::contains).count();
            // Al menos 2 valores distintos coinciden con la escala
            if (matches >= 1 && normValues.stream().anyMatch(scaleKeys::contains)) {
                // Todos los valores no vacíos están en la escala o en "no sabe"
                boolean allMatch = normValues.stream()
                        .allMatch(v -> scaleKeys.contains(v) || v.equalsIgnoreCase("no sabe"));
                if (allMatch && matches >= 1) return entry.getKey();
            }
        }
        return null;
    }

    // ── Construcción de opciones ─────────────────────────────────────────────

    public List<String> splitMultiOptions(String value, SurveyOrigin origin) {
        if (origin == SurveyOrigin.MS_FORMS) {
            return Arrays.stream(value.split(";"))
                    .map(String::trim).filter(s -> !s.isBlank()).toList();
        }
        return splitOutsideParens(value);
    }

    private List<String> splitOutsideParens(String raw) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                int j = i + 1;
                if (j < raw.length() && Character.isWhitespace(raw.charAt(j))) {
                    String part = raw.substring(start, i).trim();
                    if (!part.isBlank()) parts.add(part);
                    while (j < raw.length() && Character.isWhitespace(raw.charAt(j))) j++;
                    start = j;
                    i = j - 1;
                }
            }
        }
        String last = raw.substring(start).trim();
        if (!last.isBlank()) parts.add(last);
        return parts;
    }

    private List<OptionDef> extractMultipleOptions(List<String> values, SurveyOrigin origin) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        Map<String, String> bestDisplay = new LinkedHashMap<>();
        for (String v : values) {
            if (v.isBlank()) continue;
            for (String part : splitMultiOptions(v, origin)) {
                String key = normalize(part);
                freq.merge(key, 1, Integer::sum);
                bestDisplay.putIfAbsent(key, part.trim());
            }
        }
        int pos = 0;
        List<OptionDef> opts = new ArrayList<>();
        for (String key : freq.keySet()) {
            opts.add(new OptionDef(key, bestDisplay.getOrDefault(key, capitalize(key)), pos++, null));
        }
        return opts;
    }

    private List<OptionDef> buildNominalOptions(List<String> values) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String v : values) {
            if (!v.isBlank()) freq.merge(normalize(v), 1, Integer::sum);
        }
        // Ordena por frecuencia descendente; el display text usa la variante más frecuente del raw
        Map<String, String> bestDisplay = new LinkedHashMap<>();
        for (String v : values) {
            if (!v.isBlank()) bestDisplay.putIfAbsent(normalize(v), v.trim());
        }
        int pos = 0;
        List<OptionDef> opts = new ArrayList<>();
        for (String key : freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList()) {
            opts.add(new OptionDef(key, bestDisplay.getOrDefault(key, key), pos++, null));
        }
        return opts;
    }

    private List<OptionDef> buildLikertOptions(String scaleName) {
        Map<String, Integer> scale = LIKERT_SCALES.get(scaleName);
        if (scale == null) return List.of();
        int pos = 0;
        List<OptionDef> opts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : scale.entrySet()) {
            opts.add(new OptionDef(e.getKey(), capitalize(e.getKey()), pos++,
                    e.getValue() == null ? null : BigDecimal.valueOf(e.getValue())));
        }
        // "No sabe" para escala empleadores
        if (scaleName.equals("Empleadores 3 puntos")) {
            opts.add(new OptionDef("no sabe", "No sabe", pos, null));
        }
        return opts;
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private InferredQuestion build(String header, QuestionType type, int position,
                                    List<OptionDef> options, boolean personal) {
        InferredQuestion q = new InferredQuestion();
        q.setColumnName(header.trim());
        q.setText(cleanHeaderText(header));
        q.setType(type);
        q.setPosition(position);
        q.setPersonalData(personal);
        q.setOptions(options);
        return q;
    }

    private List<String> collectNonEmpty(String header, List<Map<String, String>> rows) {
        return rows.stream()
                .map(r -> r.getOrDefault(header, "").trim())
                .filter(v -> !v.isBlank())
                .collect(Collectors.toList());
    }

    private long distinctCount(List<String> values) {
        return values.stream().filter(v -> !v.isBlank()).map(this::normalize).distinct().count();
    }

    String normalize(String s) {
        if (s == null) return "";
        return s.trim()
                .toLowerCase()
                .replace("\n", " ")
                .replace("\r", " ")
                .replaceAll("\\s+", " ")
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ü", "u")
                .replace("ñ", "n").replace("à", "a").replace("è", "e");
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String cleanHeaderText(String header) {
        // Quita saltos de línea y espacios extra; quita corchetes de items de matriz
        String cleaned = header.replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    // ── Clases internas ──────────────────────────────────────────────────────

    public static class InferredQuestion {
        private String columnName;
        private String text;
        private QuestionType type;
        private int position;
        private boolean personalData;
        private List<OptionDef> options = new ArrayList<>();
        private String likertScaleName;
        private String matrixGroupName;

        public String getColumnName() { return columnName; }
        public void setColumnName(String v) { this.columnName = v; }

        public String getText() { return text; }
        public void setText(String v) { this.text = v; }

        public QuestionType getType() { return type; }
        public void setType(QuestionType v) { this.type = v; }

        public int getPosition() { return position; }
        public void setPosition(int v) { this.position = v; }

        public boolean isPersonalData() { return personalData; }
        public void setPersonalData(boolean v) { this.personalData = v; }

        public List<OptionDef> getOptions() { return options; }
        public void setOptions(List<OptionDef> v) { this.options = v; }

        public String getLikertScaleName() { return likertScaleName; }
        public void setLikertScaleName(String v) { this.likertScaleName = v; }

        public String getMatrixGroupName() { return matrixGroupName; }
        public void setMatrixGroupName(String v) { this.matrixGroupName = v; }
    }

    public static class OptionDef {
        private final String value;
        private final String displayText;
        private final int position;
        private final BigDecimal numericValue;

        public OptionDef(String value, String displayText, int position, BigDecimal numericValue) {
            this.value = value;
            this.displayText = displayText;
            this.position = position;
            this.numericValue = numericValue;
        }

        public String getValue() { return value; }
        public String getDisplayText() { return displayText; }
        public int getPosition() { return position; }
        public BigDecimal getNumericValue() { return numericValue; }
    }
}
