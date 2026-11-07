package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * telugu_calendar: the Telugu date, offline. Sun and moon longitudes come from Meeus' Astronomical Algorithms (the
 * largest terms), made sidereal with the Lahiri ayanamsa. Tithi and nakshatra are the ones in force at sunrise, as
 * a panchangam gives them, and the month follows the amanta rule: it runs from one new moon to the next and is named
 * after the sun's sign at the new moon that starts it. Two new moons in one sign make the first month an adhika one.
 */
final class Panchangam {
    private static final String[] WEEKDAYS = {"సోమవారం", "మంగళవారం", "బుధవారం", "గురువారం", "శుక్రవారం", "శనివారం", "ఆదివారం"};
    private static final String[] GREGORIAN = {"జనవరి", "ఫిబ్రవరి", "మార్చి", "ఏప్రిల్", "మే", "జూన్", "జులై", "ఆగస్టు",
            "సెప్టెంబర్", "అక్టోబర్", "నవంబర్", "డిసెంబర్"};
    private static final String[] MONTHS = {"చైత్రం", "వైశాఖం", "జ్యేష్ఠం", "ఆషాఢం", "శ్రావణం", "భాద్రపదం", "ఆశ్వయుజం", "కార్తీకం",
            "మార్గశిరం", "పుష్యం", "మాఘం", "ఫాల్గుణం"};
    private static final String[] TITHIS = {"పాడ్యమి", "విదియ", "తదియ", "చవితి", "పంచమి", "షష్ఠి", "సప్తమి", "అష్టమి", "నవమి",
            "దశమి", "ఏకాదశి", "ద్వాదశి", "త్రయోదశి", "చతుర్దశి"};
    private static final String[] NAKSHATRAS = {"అశ్విని", "భరణి", "కృత్తిక", "రోహిణి", "మృగశిర", "ఆర్ద్ర", "పునర్వసు", "పుష్యమి",
            "ఆశ్లేష", "మఖ", "పుబ్బ", "ఉత్తర", "హస్త", "చిత్త", "స్వాతి", "విశాఖ", "అనూరాధ", "జ్యేష్ఠ", "మూల", "పూర్వాషాఢ",
            "ఉత్తరాషాఢ", "శ్రవణం", "ధనిష్ఠ", "శతభిషం", "పూర్వాభాద్ర", "ఉత్తరాభాద్ర", "రేవతి"};
    /** Hyderabad, when the theme sets no calendar_latitude and calendar_longitude. */
    static final double DEFAULT_LATITUDE = 17.385, DEFAULT_LONGITUDE = 78.487;

    /** D, M, M', F and the sine coefficient in millionths of a degree (Meeus, table 47.A). */
    private static final int[][] MOON = {
            {0, 0, 1, 0, 6288774}, {2, 0, -1, 0, 1274027}, {2, 0, 0, 0, 658314}, {0, 0, 2, 0, 213618},
            {0, 1, 0, 0, -185116}, {0, 0, 0, 2, -114332}, {2, 0, -2, 0, 58793}, {2, -1, -1, 0, 57066},
            {2, 0, 1, 0, 53322}, {2, -1, 0, 0, 45758}, {0, 1, -1, 0, -40923}, {1, 0, 0, 0, -34720},
            {0, 1, 1, 0, -30383}, {2, 0, 0, -2, 15327}, {0, 0, 1, 2, -12528}, {0, 0, 1, -2, 10980},
            {4, 0, -1, 0, 10675}, {0, 0, 3, 0, 10034}, {4, 0, -2, 0, 8548}, {2, 1, -1, 0, -7888},
            {2, 1, 0, 0, -6766}, {1, 0, -1, 0, -5163}, {1, 1, 0, 0, 4987}, {2, -1, 1, 0, 4036}};

    /** month 0 is Chaitra; tithi 0 to 29, 14 the full moon and 29 the new moon; nakshatra 0 is Ashwini. */
    static final class Day {
        final int month, tithi, nakshatra;
        final boolean adhika;

        Day(int month, boolean adhika, int tithi, int nakshatra) {
            this.month = month;
            this.adhika = adhika;
            this.tithi = tithi;
            this.nakshatra = nakshatra;
        }
    }

    private Panchangam() {
    }

    static Day at(LocalDate date, double latitude, double longitude, ZoneId zone) {
        int[] sun = Sun.riseAndSet(date, latitude, longitude, zone);
        int minutes = sun == null ? 360 : sun[0];
        ZonedDateTime rise = date.atStartOfDay(zone).plusMinutes(minutes);
        double jd = rise.toEpochSecond() / 86400.0 + 2440587.5;
        double moon = sidereal(moon(jd), jd);
        double start = newMoonBefore(jd);
        double next = refine(start + 29.53);
        int sign = sign(start), nextSign = sign(next);
        return new Day((sign + 1) % 12, sign == nextSign, (int) (elongation(jd) / 12.0),
                (int) (moon / (360.0 / 27.0)));
    }

    /** The tithi just before midnight, for what is kept at night. */
    static int nightTithi(LocalDate date, ZoneId zone) {
        ZonedDateTime night = date.atTime(23, 59).atZone(zone);
        return (int) (elongation(night.toEpochSecond() / 86400.0 + 2440587.5) / 12.0);
    }

    /** The sun's sidereal sign at sunset, 0 Mesha to 11 Meena: a sankranti before sunset counts that day. */
    static int sunSign(LocalDate date, double latitude, double longitude, ZoneId zone) {
        int[] sun = Sun.riseAndSet(date, latitude, longitude, zone);
        ZonedDateTime set = date.atStartOfDay(zone).plusMinutes(sun == null ? 1080 : sun[1]);
        return sign(set.toEpochSecond() / 86400.0 + 2440587.5);
    }

    /** "శనివారం, 7 నవంబర్" */
    static String dateLine(LocalDate date) {
        return WEEKDAYS[date.getDayOfWeek().getValue() - 1] + ", " + date.getDayOfMonth() + " "
                + GREGORIAN[date.getMonthValue() - 1];
    }

    /** "కార్తీక మాసం · శుక్ల పాడ్యమి · అశ్విని నక్షత్రం" */
    static String panchangLine(Day day) {
        String month = (day.adhika ? "అధిక " : "") + MONTHS[day.month];
        String tithi = day.tithi == 14 ? "పౌర్ణమి" : day.tithi == 29 ? "అమావాస్య"
                : (day.tithi < 14 ? "శుక్ల " : "బహుళ ") + TITHIS[day.tithi % 15];
        return month + " · " + tithi + " · " + NAKSHATRAS[day.nakshatra] + " నక్షత్రం";
    }

    private static double newMoonBefore(double jd) {
        return refine(jd - elongation(jd) / 12.19);
    }

    /** Walks to the new moon closest to {@code jd}. */
    private static double refine(double jd) {
        for (int i = 0; i < 8; i++) {
            double e = elongation(jd);
            jd -= (e > 180 ? e - 360 : e) / 12.19;
        }
        return jd;
    }

    private static int sign(double jd) {
        return (int) (sidereal(sun(jd), jd) / 30.0);
    }

    /** The moon ahead of the sun, 0 to 360 degrees. */
    private static double elongation(double jd) {
        return norm(moon(jd) - sun(jd));
    }

    private static double sidereal(double tropical, double jd) {
        return norm(tropical - (23.853 + 0.013969 * centuries(jd) * 100));
    }

    /** Julian centuries of dynamical time since J2000; the 69 s is today's difference from clock time. */
    private static double centuries(double jd) {
        return (jd + 69.0 / 86400.0 - 2451545.0) / 36525.0;
    }

    static double sun(double jd) {
        double t = centuries(jd);
        double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        double m = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t);
        double c = (1.914602 - 0.004817 * t) * Math.sin(m) + 0.019993 * Math.sin(2 * m) + 0.000289 * Math.sin(3 * m);
        double omega = Math.toRadians(125.04 - 1934.136 * t);
        return norm(l0 + c - 0.00569 - 0.00478 * Math.sin(omega));
    }

    static double moon(double jd) {
        double t = centuries(jd);
        double lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t * t;
        double d = 297.8501921 + 445267.1114034 * t - 0.0018819 * t * t;
        double m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t * t;
        double mp = 134.9633964 + 477198.8675055 * t + 0.0087414 * t * t;
        double f = 93.2720950 + 483202.0175233 * t - 0.0036539 * t * t;
        double e = 1 - 0.002516 * t;
        double sum = 0;
        for (int[] term : MOON) {
            double angle = Math.toRadians(term[0] * d + term[1] * m + term[2] * mp + term[3] * f);
            sum += term[4] * Math.pow(e, Math.abs(term[1])) * Math.sin(angle);
        }
        sum += 3958 * Math.sin(Math.toRadians(119.75 + 131.849 * t)) + 1962 * Math.sin(Math.toRadians(lp - f))
                + 318 * Math.sin(Math.toRadians(53.09 + 479264.29 * t));
        return norm(lp + sum / 1000000.0);
    }

    private static double norm(double degrees) {
        double d = degrees % 360.0;
        return d < 0 ? d + 360.0 : d;
    }
}
