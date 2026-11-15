package com.jhaseft.tss.service;

import java.util.List;
import java.util.Map;

/**
 * Variable lista para cruzar: su texto, su tipo, las etiquetas de categoría en el orden del eje y la
 * categoría de cada submission. La produce tanto una pregunta real como una variable derivada
 * (p. ej. el segmento de experiencia recodificado desde "años de vida profesional").
 */
public record ResolvedVariable(
        String text,
        String type,
        List<String> orderedLabels,
        Map<Long, String> bySubmission) {
}
