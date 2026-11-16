package com.jhaseft.tss.stats;

import com.jhaseft.tss.service.stats.WilsonInterval;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Valores de referencia del Informe de Calidad, punto 8. */
class WilsonIntervalTest {

    @Test
    void proporcionDeMujeres() {
        WilsonInterval.Result r = WilsonInterval.of(33, 148);
        assertEquals(22.3, r.pct(), 0.1);
        assertEquals(16.3, r.low(), 0.2);
        assertEquals(29.7, r.high(), 0.2);
    }

    @Test
    void preveeContratar() {
        WilsonInterval.Result r = WilsonInterval.of(68, 80);
        assertEquals(85.0, r.pct(), 0.1);
        assertEquals(75.6, r.low(), 0.3);
        assertEquals(91.2, r.high(), 0.3);
    }
}
