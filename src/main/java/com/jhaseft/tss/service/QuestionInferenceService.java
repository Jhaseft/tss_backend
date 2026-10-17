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

        return result;
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
        // 7. Opción única (pocos valores distintos)
        long distinct = distinctCount(values);
        if (distinct > 0 && distinct <= 15 && values.size() >= 1) {
            List<OptionDef> opts = buildNominalOptions(values);
            return build(header, QuestionType.SINGLE_NOMINAL, position, opts, false);
        }
        // 8. Texto largo / corto
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
        // Google Forms: encabezado dice "puede seleccionar" o "seleccione"
        if (headerNorm.contains("puede seleccionar") || headerNorm.contains("seleccione") || headerNorm.contains("marque")) {
            return true;
        }
        // MS Forms: algún valor contiene ';' (separador interno de múltiple)
        if (origin == SurveyOrigin.MS_FORMS) {
            return values.stream().anyMatch(v -> v.contains(";") && v.split(";").length > 1);
        }
        // Google Forms: algún valor contiene ", " y tiene múltiples fragmentos
        if (origin == SurveyOrigin.GOOGLE_FORMS) {
            return values.stream().anyMatch(v -> v.contains(", ") && v.split(", ").length > 1);
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

    private List<OptionDef> extractMultipleOptions(List<String> values, SurveyOrigin origin) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String v : values) {
            if (v.isBlank()) continue;
            List<String> parts;
            if (origin == SurveyOrigin.MS_FORMS) {
                parts = Arrays.stream(v.split(";")).map(String::trim).filter(s -> !s.isBlank()).toList();
            } else {
                parts = Arrays.stream(v.split(",\\s+")).map(String::trim).filter(s -> !s.isBlank()).toList();
            }
            for (String part : parts) {
                freq.merge(normalize(part), 1, Integer::sum);
            }
        }
        int pos = 0;
        List<OptionDef> opts = new ArrayList<>();
        for (String key : freq.keySet()) {
            opts.add(new OptionDef(key, capitalize(key), pos++, null));
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
