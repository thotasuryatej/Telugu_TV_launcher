package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.Test;

/** The festival that comes out for dates whose festival is known. */
public class FestivalCalendarTest {
    private static FestivalCalendar.Entry on(int y, int m, int d) {
        return FestivalCalendar.on(LocalDate.of(y, m, d), Panchangam.DEFAULT_LATITUDE, Panchangam.DEFAULT_LONGITUDE,
                ZoneId.of("Asia/Kolkata"));
    }

    private static void assertFestival(String greetingStart, int effect, int y, int m, int d) {
        FestivalCalendar.Entry e = on(y, m, d);
        assertNotNull(y + "-" + m + "-" + d, e);
        assertEquals(greetingStart + " శుభాకాంక్షలు", e.greeting);
        assertEquals(effect, e.effect);
    }

    @Test public void sankrantiDays() {
        assertFestival("భోగి", FestivalCalendar.KITES, 2026, 1, 13);
        assertFestival("సంక్రాంతి", FestivalCalendar.KITES, 2026, 1, 14);
        assertFestival("కనుమ", FestivalCalendar.KITES, 2026, 1, 15);
    }

    @Test public void shivaratriIsKeptAtNight() {
        assertFestival("మహా శివరాత్రి", FestivalCalendar.SPARKS, 2026, 2, 15);
    }

    @Test public void holi() {
        assertFestival("హోలీ", FestivalCalendar.COLOURS, 2026, 3, 4);
    }

    @Test public void ugadiAndRamaNavami() {
        assertFestival("ఉగాది", FestivalCalendar.LEAVES, 2025, 3, 30);
        assertFestival("శ్రీరామ నవమి", FestivalCalendar.PETALS, 2025, 4, 6);
    }

    @Test public void vinayakaChavithi() {
        assertFestival("వినాయక చవితి", FestivalCalendar.PETALS, 2025, 8, 27);
    }

    @Test public void bathukammaAndDasara() {
        assertFestival("బతుకమ్మ", FestivalCalendar.FLOWERS, 2025, 9, 21);
        assertFestival("దసరా", FestivalCalendar.PETALS, 2025, 10, 2);
    }

    @Test public void deepavali() {
        assertFestival("దీపావళి", FestivalCalendar.SPARKS, 2025, 10, 20);
        assertFestival("దీపావళి", FestivalCalendar.SPARKS, 2026, 11, 8);
    }

    @Test public void karthikaPournami() {
        assertFestival("కార్తీక పౌర్ణమి", FestivalCalendar.SPARKS, 2025, 11, 5);
    }

    @Test public void telanganaAndOtherFestivals() {
        assertFestival("హనుమాన్ జయంతి", FestivalCalendar.PETALS, 2025, 4, 12);
        assertFestival("అక్షయ తృతీయ", FestivalCalendar.SPARKS, 2025, 4, 30);
        assertFestival("తొలి ఏకాదశి", FestivalCalendar.PETALS, 2025, 7, 6);
        assertFestival("గురు పౌర్ణమి", FestivalCalendar.PETALS, 2025, 7, 10);
        assertFestival("బోనాలు", FestivalCalendar.FLOWERS, 2025, 7, 13);
        assertFestival("నాగుల పంచమి", FestivalCalendar.PETALS, 2025, 7, 29);
        assertFestival("వరలక్ష్మీ వ్రతం", FestivalCalendar.FLOWERS, 2025, 8, 8);
        assertFestival("రాఖీ పౌర్ణమి", FestivalCalendar.FLOWERS, 2025, 8, 9);
        assertFestival("శ్రీకృష్ణాష్టమి", FestivalCalendar.PETALS, 2025, 8, 16);
        assertFestival("గణేష్ నిమజ్జనం", FestivalCalendar.PETALS, 2025, 9, 6);
        assertFestival("సద్దుల బతుకమ్మ", FestivalCalendar.FLOWERS, 2025, 9, 30);
        assertFestival("నాగుల చవితి", FestivalCalendar.PETALS, 2025, 10, 25);
        assertFestival("ముక్కోటి ఏకాదశి", FestivalCalendar.SPARKS, 2025, 12, 30);
        assertFestival("వసంత పంచమి", FestivalCalendar.FLOWERS, 2026, 1, 23);
        assertFestival("రథ సప్తమి", FestivalCalendar.SPARKS, 2026, 1, 25);
    }

    @Test public void newYearBothDays() {
        assertFestival("నూతన సంవత్సర", FestivalCalendar.CONFETTI, 2026, 12, 31);
        assertFestival("నూతన సంవత్సర", FestivalCalendar.CONFETTI, 2027, 1, 1);
    }

    @Test public void familyDatesCountTheYears() {
        java.util.List<String> days = java.util.Arrays.asList("01-15|2005|birthday|A", "06-20|2001|anniversary|B - C");
        assertEquals("A 22వ పుట్టినరోజు శుభాకాంక్షలు",
                FestivalCalendar.family(LocalDate.of(2027, 1, 15), days).greeting);
        assertEquals(FestivalCalendar.BALLOONS, FestivalCalendar.family(LocalDate.of(2027, 1, 15), days).effect);
        assertEquals("B - C 26వ పెళ్లిరోజు శుభాకాంక్షలు",
                FestivalCalendar.family(LocalDate.of(2027, 6, 20), days).greeting);
        assertEquals(FestivalCalendar.HEARTS, FestivalCalendar.family(LocalDate.of(2027, 6, 20), days).effect);
        assertNull(FestivalCalendar.family(LocalDate.of(2027, 1, 16), days));
    }

    @Test public void aBadFamilyEntryIsSkipped() {
        assertNull(FestivalCalendar.family(LocalDate.of(2027, 1, 15), java.util.Arrays.asList("01-15|soon|birthday|A")));
    }

    @Test public void correctionsMoveAFestival() {
        java.util.List<String> fixes = java.util.Arrays.asList("2030-01-02|none", "2030-01-03|Name|3");
        assertEquals(true, FestivalCalendar.noFestival(LocalDate.of(2030, 1, 2), fixes));
        assertEquals(false, FestivalCalendar.noFestival(LocalDate.of(2030, 1, 3), fixes));
        assertNull(FestivalCalendar.corrected(LocalDate.of(2030, 1, 2), fixes));
        assertEquals("Name శుభాకాంక్షలు", FestivalCalendar.corrected(LocalDate.of(2030, 1, 3), fixes).greeting);
        assertEquals(FestivalCalendar.KITES, FestivalCalendar.corrected(LocalDate.of(2030, 1, 3), fixes).effect);
    }

    @Test public void aLongGreetingIsKeptWhole() {
        String wish = "నాగరాజు - సంధ్యారాణి 26వ పెళ్లిరోజు శుభాకాంక్షలు";
        assertEquals(wish, Festival.parse("family", "2030-01-01", wish, "").greeting);
    }

    @Test public void anOrdinaryDayHasNone() {
        assertNull(on(2026, 7, 8));
    }
}
