package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * festival_auto: which Telugu festival today is, worked out from Panchangam, so it needs no updates or internet. A
 * tithi counts at sunrise, the panchangam's rule, except Maha Shivaratri, which is kept at night. Dates that local
 * custom moves by a day can differ from a printed calendar.
 */
final class FestivalCalendar {
    static final int PETALS = 0, SPARKS = 1, LEAVES = 2, KITES = 3, FLOWERS = 4, COLOURS = 5, BALLOONS = 6, HEARTS = 7,
            CONFETTI = 8;

    /** One festival: its greeting, the skin's name, the accent colour and the animation that goes with it. */
    static final class Entry {
        final String greeting, skin, accent;
        final int effect;

        Entry(String greeting, String skin, String accent, int effect) {
            this.greeting = greeting;
            this.skin = skin;
            this.accent = accent;
            this.effect = effect;
        }
    }

    private static final int JANUARY_SIGN = 9;   // Makara
    private static final int CHAITRA = 0, VAISAKHA = 1, ASHADHA = 3, SHRAVANA = 4, BHADRAPADA = 5, ASHWAYUJA = 6, KARTIKA = 7,
            MAGHA = 10, PHALGUNA = 11;
    private static final int DHANUS = 8;

    private FestivalCalendar() {
    }

    /**
     * family_days: a date of the owner's, each entry "MM-dd|year|birthday or anniversary|name". The count comes from
     * the year, so it stays right every year. Null when none falls today or an entry cannot be read.
     */
    static Entry family(LocalDate date, List<String> days) {
        String today = String.format(java.util.Locale.ROOT, "%02d-%02d", date.getMonthValue(), date.getDayOfMonth());
        for (String day : days) {
            String[] part = day.split("\\|", 4);
            if (part.length != 4 || !part[0].trim().equals(today)) continue;
            int count;
            try {
                count = date.getYear() - Integer.parseInt(part[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            String name = part[3].trim();
            return "anniversary".equals(part[2].trim())
                    ? new Entry(name + " " + count + "వ పెళ్లిరోజు శుభాకాంక్షలు", "family", "#FF6F91", HEARTS)
                    : new Entry(name + " " + count + "వ పుట్టినరోజు శుభాకాంక్షలు", "family", "#FFC24B", BALLOONS);
        }
        return null;
    }

    /** Whether festival_corrections says today has no festival: an entry "yyyy-MM-dd|none". */
    static boolean noFestival(LocalDate date, List<String> corrections) {
        for (String c : corrections) {
            String[] part = c.split("\\|", 3);
            if (part.length >= 2 && part[0].trim().equals(date.toString()) && "none".equals(part[1].trim())) return true;
        }
        return false;
    }

    /**
     * festival_corrections: "yyyy-MM-dd|festival name|effect" puts a festival on a date whatever the calculation says.
     * The effect is a number as above; a missing or unreadable one is petals.
     */
    static Entry corrected(LocalDate date, List<String> corrections) {
        for (String c : corrections) {
            String[] part = c.split("\\|", 3);
            if (part.length < 2 || !part[0].trim().equals(date.toString()) || "none".equals(part[1].trim())) continue;
            int effect = PETALS;
            if (part.length == 3) {
                try {
                    effect = Integer.parseInt(part[2].trim());
                } catch (NumberFormatException ignored) {
                    // Petals.
                }
            }
            return wish(part[1].trim(), "#FFB23C", effect);
        }
        return null;
    }

    /** Today's festival, or null on an ordinary day. */
    static Entry on(LocalDate date, double lat, double lon, ZoneId zone) {
        // The last day of the year and the first day of the next.
        if ((date.getMonthValue() == 12 && date.getDayOfMonth() == 31)
                || (date.getMonthValue() == 1 && date.getDayOfMonth() == 1)) {
            return new Entry("నూతన సంవత్సర శుభాకాంక్షలు", "festival", "#FFD36B", CONFETTI);
        }
        Panchangam.Day d = Panchangam.at(date, lat, lon, zone);
        Panchangam.Day y = Panchangam.at(date.minusDays(1), lat, lon, zone);

        if (sankranti(date.plusDays(1), lat, lon, zone)) return kite("భోగి శుభాకాంక్షలు");
        if (sankranti(date, lat, lon, zone)) return kite("సంక్రాంతి శుభాకాంక్షలు");
        if (sankranti(date.minusDays(1), lat, lon, zone)) return kite("కనుమ శుభాకాంక్షలు");

        if (!d.adhika) {
            if (d.month == MAGHA && Panchangam.nightTithi(date, zone) == 28) {
                return new Entry("మహా శివరాత్రి శుభాకాంక్షలు", "festival", "#7FB7FF", SPARKS);
            }
            if (is(d, y, CHAITRA, 0)) return new Entry("ఉగాది శుభాకాంక్షలు", "festival", "#7BC96F", LEAVES);
            if (is(d, y, CHAITRA, 14)) return wish("హనుమాన్ జయంతి", "#FF9A3C", PETALS);
            if (is(d, y, VAISAKHA, 2)) return wish("అక్షయ తృతీయ", "#FFD36B", SPARKS);
            if (is(d, y, ASHADHA, 10)) return wish("తొలి ఏకాదశి", "#FFB23C", PETALS);
            if (is(d, y, ASHADHA, 14)) return wish("గురు పౌర్ణమి", "#FFD36B", PETALS);
            // Telangana keeps Bonalu on the Sundays of Ashadha.
            if (d.month == ASHADHA && date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                return wish("బోనాలు", "#FF7043", FLOWERS);
            }
            if (is(d, y, SHRAVANA, 4)) return wish("నాగుల పంచమి", "#7BC96F", PETALS);
            if (is(d, y, SHRAVANA, 14)) return wish("రాఖీ పౌర్ణమి", "#F06DB0", FLOWERS);
            // The Friday just before Shravana's full moon.
            if (d.month == SHRAVANA && d.tithi >= 7 && d.tithi < 14
                    && date.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) {
                return wish("వరలక్ష్మీ వ్రతం", "#FFB23C", FLOWERS);
            }
            if (is(d, y, SHRAVANA, 22)) return wish("శ్రీకృష్ణాష్టమి", "#5C9DFF", PETALS);
            if (is(d, y, BHADRAPADA, 13)) return wish("గణేష్ నిమజ్జనం", "#FF9A3C", PETALS);
            if (d.month == ASHWAYUJA && d.tithi == 7) return wish("సద్దుల బతుకమ్మ", "#F06DB0", FLOWERS);
            if (d.month == ASHWAYUJA && d.tithi == 8) return wish("మహా నవమి", "#FFB23C", PETALS);
            if (is(d, y, KARTIKA, 3)) return wish("నాగుల చవితి", "#7BC96F", PETALS);
            // In Dhanurmasa, by the sun: its bright-half Ekadashi can fall in either lunar month.
            // An Ekadashi that begins after sunrise and ends before the next one counts on the day it begins.
            if ((d.tithi == 10 || (d.tithi == 9 && Panchangam.at(date.plusDays(1), lat, lon, zone).tithi == 11))
                    && Panchangam.sunSign(date, lat, lon, zone) == DHANUS) {
                return wish("ముక్కోటి ఏకాదశి", "#FFD36B", SPARKS);
            }
            if (is(d, y, MAGHA, 4)) return wish("వసంత పంచమి", "#FFD54F", FLOWERS);
            if (is(d, y, MAGHA, 6)) return wish("రథ సప్తమి", "#FFB23C", SPARKS);
            if (is(d, y, CHAITRA, 8)) return new Entry("శ్రీరామ నవమి శుభాకాంక్షలు", "festival", "#FFB23C", PETALS);
            if (is(d, y, BHADRAPADA, 3)) return new Entry("వినాయక చవితి శుభాకాంక్షలు", "festival", "#FF9A3C", PETALS);
            if ((d.month == BHADRAPADA && d.tithi == 29) || (d.month == ASHWAYUJA && d.tithi <= 7)) {
                return new Entry("బతుకమ్మ శుభాకాంక్షలు", "festival", "#F06DB0", FLOWERS);
            }
            if (d.month == ASHWAYUJA && d.tithi >= 8 && d.tithi <= 9) {
                return new Entry("దసరా శుభాకాంక్షలు", "dussehra", "#FFB23C", PETALS);
            }
            if (d.month == ASHWAYUJA && d.tithi >= 27) {
                return new Entry("దీపావళి శుభాకాంక్షలు", "diwali", "#FFB23C", SPARKS);
            }
            if (is(d, y, KARTIKA, 14)) return new Entry("కార్తీక పౌర్ణమి శుభాకాంక్షలు", "festival", "#FFD36B", SPARKS);
            // Colours are played the day after the full moon of Phalguna.
            if (!y.adhika && y.month == PHALGUNA && y.tithi == 14) {
                return new Entry("హోలీ శుభాకాంక్షలు", "festival", "#FF5FA2", COLOURS);
            }
        }
        return null;
    }

    private static Entry wish(String festival, String accent, int effect) {
        return new Entry(festival + " శుభాకాంక్షలు", "festival", accent, effect);
    }

    private static Entry kite(String greeting) {
        return new Entry(greeting, "festival", "#FFB23C", KITES);
    }

    /** That tithi at sunrise, or skipped between yesterday's sunrise and today's. */
    private static boolean is(Panchangam.Day d, Panchangam.Day y, int month, int tithi) {
        return d.month == month && (d.tithi == tithi || (y.tithi == (tithi + 29) % 30 && d.tithi == tithi + 1));
    }

    private static boolean sankranti(LocalDate date, double lat, double lon, ZoneId zone) {
        return Panchangam.sunSign(date, lat, lon, zone) == JANUARY_SIGN
                && Panchangam.sunSign(date.minusDays(1), lat, lon, zone) == JANUARY_SIGN - 1;
    }
}
