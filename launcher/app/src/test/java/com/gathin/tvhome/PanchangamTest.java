package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.Test;

/** Festivals whose Telugu date is fixed: the month and tithi must come out right at sunrise in Hyderabad. */
public class PanchangamTest {
    private static final int CHAITRA = 0, JYESHTHA = 2, BHADRAPADA = 5, ASHWAYUJA = 6;

    private static Panchangam.Day on(int year, int month, int day) {
        return Panchangam.at(LocalDate.of(year, month, day), Panchangam.DEFAULT_LATITUDE,
                Panchangam.DEFAULT_LONGITUDE, ZoneId.of("Asia/Kolkata"));
    }

    @Test public void ugadi() {
        Panchangam.Day d = on(2025, 3, 30);
        assertEquals(CHAITRA, d.month);
        assertEquals(0, d.tithi);
        assertEquals(26, d.nakshatra);   // Revati
        assertEquals(CHAITRA, on(2026, 3, 20).month);
    }

    @Test public void sriRamaNavami() {
        Panchangam.Day d = on(2025, 4, 6);
        assertEquals(CHAITRA, d.month);
        assertEquals(8, d.tithi);
    }

    @Test public void vinayakaChavithi() {
        Panchangam.Day d = on(2025, 8, 27);
        assertEquals(BHADRAPADA, d.month);
        assertEquals(3, d.tithi);
    }

    @Test public void vijayadashami() {
        Panchangam.Day d = on(2025, 10, 2);
        assertEquals(ASHWAYUJA, d.month);
        assertEquals(9, d.tithi);
    }

    @Test public void deepavaliIsInTheDarkHalfOfAshwayuja() {
        Panchangam.Day d = on(2025, 10, 20);
        assertEquals(ASHWAYUJA, d.month);
        assertEquals(28, d.tithi);   // Chaturdashi at sunrise; the new moon comes later that day
    }

    @Test public void adhikaJyeshtha2026() {
        Panchangam.Day leap = on(2026, 5, 25);
        assertEquals(JYESHTHA, leap.month);
        assertTrue(leap.adhika);
        Panchangam.Day regular = on(2026, 6, 25);
        assertEquals(JYESHTHA, regular.month);
        assertFalse(regular.adhika);
    }

    @Test public void linesReadAsTelugu() {
        assertEquals("శనివారం, 7 నవంబర్", Panchangam.dateLine(LocalDate.of(2026, 11, 7)));
        assertEquals("చైత్రం · శుక్ల పాడ్యమి · రేవతి నక్షత్రం", Panchangam.panchangLine(on(2025, 3, 30)));
    }

    @Test public void fullAndNewMoonHaveTheirOwnNames() {
        assertEquals("చైత్రం · పౌర్ణమి · అశ్విని నక్షత్రం",
                Panchangam.panchangLine(new Panchangam.Day(CHAITRA, false, 14, 0)));
        assertEquals("చైత్రం · అమావాస్య · అశ్విని నక్షత్రం",
                Panchangam.panchangLine(new Panchangam.Day(CHAITRA, false, 29, 0)));
        assertEquals("చైత్రం · బహుళ పాడ్యమి · అశ్విని నక్షత్రం",
                Panchangam.panchangLine(new Panchangam.Day(CHAITRA, false, 15, 0)));
    }
}
