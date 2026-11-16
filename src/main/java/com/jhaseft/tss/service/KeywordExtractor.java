package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.FrequencyEntryDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Agrupa respuestas de texto libre por tema (IA, Ciberseguridad, Ciencia de Datos, …) y cuenta
 * cuántas respuestas mencionan cada tema. Usa un vocabulario común, por lo que sirve para comparar
 * el texto libre de encuestas distintas (p. ej. áreas/competencias que demandan los empleadores vs
 * las que mencionan los titulados). Los valores deben venir normalizados (minúsculas, sin tildes).
 */
@Component
public class KeywordExtractor {

    private static final Map<String, Pattern> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put("IA / Agentes", Pattern.compile("\\bia\\b|\\bai\\b|inteligencia artif|inteligente artif|machine learning|maching learning|\\bmlops\\b|\\bml\\b|\\bllm|prompt|agentic|agentes|deep learning|redes neuronales"));
        GROUPS.put("Ciberseguridad", Pattern.compile("ciberseguridad|ciber seguridad|seguridad inform|seguridad de sistemas|seguridad|forense|forence|pentest|hacking"));
        GROUPS.put("Ciencia de Datos", Pattern.compile("ciencia de datos|analisis de datos|analisis se datos|big data|data warehouse|ingenier[oa]s de datos|analitica|\\bdatos\\b|\\bbi\\b|business intelligence|power bi|tableau"));
        GROUPS.put("Automatización / IoT", Pattern.compile("automatizacion|\\biot\\b|internet de las cosas|\\brpa\\b|robotica"));
        GROUPS.put("Cloud", Pattern.compile("\\bcloud\\b|\\bnube\\b|computacion en la nube|\\baws\\b|\\bazure\\b|\\bgcp\\b"));
        GROUPS.put("DevOps", Pattern.compile("devops|docker|kubernetes|ci/cd|jenkins|terraform"));
        GROUPS.put("Desarrollo Web/Móvil", Pattern.compile("\\breact\\b|angular|\\bvue\\b|node|flutter|desarrollo web|desarrollo movil|programacion movil|\\bapi\\b|microservicios|laravel|spring|\\bnest\\b"));
        GROUPS.put("Bases de Datos", Pattern.compile("base de datos|bases de datos|\\bsql\\b|postgres|mongo"));
        GROUPS.put("Lenguajes de programación", Pattern.compile("\\bpython\\b|\\bjava\\b|javascript|\\bc\\+\\+|\\bc#|\\bgo\\b"));
        GROUPS.put("Blockchain", Pattern.compile("blockchain|solidity|web3"));
        GROUPS.put("Trabajo en equipo", Pattern.compile("trabajo en equipo|colaboracion|equipos multidisc"));
        GROUPS.put("Comunicación", Pattern.compile("comunicacion"));
        GROUPS.put("Resolución de problemas", Pattern.compile("resolucion de problemas|solucion de problemas|pensamiento critico|analitic"));
        GROUPS.put("Liderazgo / Gestión", Pattern.compile("liderazgo|gestion de proyectos|\\blider"));
        GROUPS.put("Aprendizaje continuo", Pattern.compile("aprendizaje continuo|adaptab|actualizacion|autoaprendizaje"));
        GROUPS.put("Inglés", Pattern.compile("\\bingles\\b|idioma"));
        GROUPS.put("Habilidades blandas", Pattern.compile("habilidades blandas|soft skills|competencias blandas"));
    }

    /** Lista de temas (orden de definición), para alinear dos encuestas en el mismo eje. */
    public List<String> themes() {
        return new ArrayList<>(GROUPS.keySet());
    }

    /** Nº de respuestas que mencionan cada tema (0 incluido si {@code includeZero}). */
    public Map<String, Long> counts(List<String> values, boolean includeZero) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Pattern> g : GROUPS.entrySet()) {
            long c = values.stream().filter(v -> g.getValue().matcher(v).find()).count();
            if (c > 0 || includeZero) out.put(g.getKey(), c);
        }
        return out;
    }

    /** Temas presentes como tabla de frecuencias (para el dashboard), ordenados por conteo. */
    public List<FrequencyEntryDTO> extract(List<String> values, int maxThemes) {
        if (values.isEmpty()) return List.of();
        int total = values.size();
        List<FrequencyEntryDTO> out = new ArrayList<>();
        counts(values, false).forEach((theme, c) -> {
            FrequencyEntryDTO f = new FrequencyEntryDTO();
            f.setLabel(theme);
            f.setCount(c);
            f.setPercentage(c * 100.0 / total);
            out.add(f);
        });
        out.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        return out.size() > maxThemes ? new ArrayList<>(out.subList(0, maxThemes)) : out;
    }
}
