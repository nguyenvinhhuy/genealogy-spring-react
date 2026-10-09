package com.genealogy.common.util;

import java.time.LocalDate;
import java.util.Optional;

/** Converts between Gregorian and Vietnamese âm lịch: Hồ Ngọc Đức's algorithm, UTC+7 from 1968 (CLAUDE.md §3.3). */
public final class LunarCalendar {

    // Vietnam's offset in hours from 1968: the number that makes this calendar Vietnamese and not Chinese.
    private static final double VIETNAM_TIMEZONE = 7.0;

    // The offset âm lịch was computed at before 1968, when the North moved to UTC+7 (§8.10 D2).
    private static final double BEFORE_1968_TIMEZONE = 8.0;

    // The first day computed at UTC+7; Tết Mậu Thân fell on 29 January 1968 in the North and 30 in the South.
    private static final LocalDate UTC7_FROM = LocalDate.of(1968, 1, 1);

    // Mean length of a synodic month, in days.
    private static final double SYNODIC_MONTH = 29.530588853;

    // Julian day of the new moon that the lunation index counts from.
    private static final double EPOCH_NEW_MOON = 2415021.076998695;

    private static final double DEG = Math.PI / 180;

    private static final String[] HEAVENLY_STEMS =
            {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};

    private static final String[] EARTHLY_BRANCHES =
            {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"};

    /** Not instantiable. */
    private LunarCalendar() {
    }

    /**
     * Converts a Gregorian date to Vietnamese âm lịch, at the offset in force on that day.
     *
     * @param solar the Gregorian date
     * @return the lunar date
     */
    public static LunarDate toLunar(LocalDate solar) {
        return toLunar(solar, solar.isBefore(UTC7_FROM) ? BEFORE_1968_TIMEZONE : VIETNAM_TIMEZONE);
    }

    /**
     * Names a lunar year by its can-chi, the way a gia phả writes it.
     *
     * @param lunarYear the lunar year
     * @return the can-chi name, such as "Canh Dần" for 1950
     */
    public static String canChi(int lunarYear) {
        // Floor modulo: a year before year 4 still names a real stem and branch, and 1984 is Giáp Tý.
        return HEAVENLY_STEMS[Math.floorMod(lunarYear + 6, 10)] + " "
                + EARTHLY_BRANCHES[Math.floorMod(lunarYear + 8, 12)];
    }

    /**
     * Converts a Gregorian date to a lunar date at an explicit offset; tests only, use {@link #toLunar(LocalDate)}.
     *
     * @param solar the Gregorian date
     * @param timeZone the offset in hours
     * @return the lunar date
     */
    static LunarDate toLunar(LocalDate solar, double timeZone) {
        int dayNumber = julianDay(solar);
        int lunation = (int) Math.floor((dayNumber - EPOCH_NEW_MOON) / SYNODIC_MONTH);

        int monthStart = newMoonDay(lunation + 1, timeZone);
        if (monthStart > dayNumber) {
            monthStart = newMoonDay(lunation, timeZone);
        }

        int year = solar.getYear();
        int winterSolsticeMonth = lunarMonth11(year, timeZone);
        int nextWinterSolsticeMonth = winterSolsticeMonth;
        int lunarYear;
        if (winterSolsticeMonth >= monthStart) {
            lunarYear = year;
            winterSolsticeMonth = lunarMonth11(year - 1, timeZone);
        } else {
            lunarYear = year + 1;
            nextWinterSolsticeMonth = lunarMonth11(year + 1, timeZone);
        }

        int day = dayNumber - monthStart + 1;
        int monthsSinceEleven = (int) Math.floor((monthStart - winterSolsticeMonth) / 29.0);
        boolean leap = false;
        int month = monthsSinceEleven + 11;

        if (nextWinterSolsticeMonth - winterSolsticeMonth > 365) {
            int leapOffset = leapMonthOffset(winterSolsticeMonth, timeZone);
            if (monthsSinceEleven >= leapOffset) {
                month = monthsSinceEleven + 10;
                leap = monthsSinceEleven == leapOffset;
            }
        }
        if (month > 12) {
            month -= 12;
        }
        if (month >= 11 && monthsSinceEleven < 4) {
            lunarYear -= 1;
        }
        return new LunarDate(day, month, lunarYear, leap);
    }

    /**
     * Converts a Vietnamese lunar date back to the Gregorian calendar.
     *
     * @param lunar the lunar date
     * @return the Gregorian date, or empty when that lunar date does not exist in that year
     */
    public static Optional<LocalDate> toSolar(LunarDate lunar) {
        // The offset depends on the answer, so the UTC+7 reading stands unless it lands before 1968.
        Optional<LocalDate> modern = toSolar(lunar, VIETNAM_TIMEZONE);
        if (modern.isPresent() && !modern.get().isBefore(UTC7_FROM)) {
            return modern;
        }
        Optional<LocalDate> older = toSolar(lunar, BEFORE_1968_TIMEZONE);
        if (older.isPresent() && older.get().isBefore(UTC7_FROM)) {
            return older;
        }
        return modern.isPresent() ? modern : older;
    }

    /**
     * Converts a lunar date back to Gregorian at an explicit offset.
     *
     * @param lunar the lunar date
     * @param timeZone the offset in hours
     * @return the Gregorian date, or empty when that lunar date does not exist in that year
     */
    private static Optional<LocalDate> toSolar(LunarDate lunar, double timeZone) {
        int winterSolsticeMonth;
        int nextWinterSolsticeMonth;
        if (lunar.month() < 11) {
            winterSolsticeMonth = lunarMonth11(lunar.year() - 1, timeZone);
            nextWinterSolsticeMonth = lunarMonth11(lunar.year(), timeZone);
        } else {
            winterSolsticeMonth = lunarMonth11(lunar.year(), timeZone);
            nextWinterSolsticeMonth = lunarMonth11(lunar.year() + 1, timeZone);
        }

        int offset = lunar.month() - 11;
        if (offset < 0) {
            offset += 12;
        }

        if (nextWinterSolsticeMonth - winterSolsticeMonth > 365) {
            int leapOffset = leapMonthOffset(winterSolsticeMonth, timeZone);
            int leapMonth = leapOffset - 2;
            if (leapMonth < 0) {
                leapMonth += 12;
            }
            if (lunar.leapMonth() && lunar.month() != leapMonth) {
                // That year has a leap month, but not this one - the caller asked for a date that never was.
                return Optional.empty();
            }
            if (lunar.leapMonth() || offset >= leapOffset) {
                offset += 1;
            }
        } else if (lunar.leapMonth()) {
            return Optional.empty();
        }

        int lunation = (int) Math.floor(0.5 + (winterSolsticeMonth - EPOCH_NEW_MOON) / SYNODIC_MONTH);
        int monthStart = newMoonDay(lunation + offset, timeZone);
        return Optional.of(fromJulianDay(monthStart + lunar.day() - 1));
    }

    /**
     * Finds the next Gregorian date on which a lunar day-and-month falls, on or after a given day.
     *
     * @param lunarDay ngày âm of the anniversary
     * @param lunarMonth tháng âm of the anniversary
     * @param from the first Gregorian date to consider
     * @return the Gregorian date of the next occurrence
     */
    public static LocalDate nextAnniversary(int lunarDay, int lunarMonth, LocalDate from) {
        // The Gregorian day a giỗ lands on moves back about eleven days a year, so it is searched, not offset.
        LunarDate today = toLunar(from);

        for (int year = today.year(); year <= today.year() + 2; year++) {
            LocalDate candidate = resolveAnniversary(lunarDay, lunarMonth, year);
            if (!candidate.isBefore(from)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "No occurrence of lunar %d/%d found near %s".formatted(lunarDay, lunarMonth, from));
    }

    /**
     * Resolves one lunar day of an ordinary month within a lunar year, clamping a missing 30th to the 29th.
     *
     * @param lunarDay ngày âm
     * @param lunarMonth tháng âm
     * @param lunarYear năm âm
     * @return the Gregorian date
     */
    private static LocalDate resolveAnniversary(int lunarDay, int lunarMonth, int lunarYear) {
        // An ordinary month exists every lunar year, so only a leap request can come back empty.
        LocalDate exact = toSolar(LunarDate.of(lunarDay, lunarMonth, lunarYear)).orElseThrow();
        LunarDate check = toLunar(exact);
        if (check.day() == lunarDay && check.month() == lunarMonth && !check.leapMonth()) {
            return exact;
        }
        // A 30th that rolled into the next month means the month only had 29 days.
        return toSolar(LunarDate.of(29, lunarMonth, lunarYear)).orElseThrow();
    }

    /**
     * Converts a proleptic Gregorian date to its Julian day number.
     *
     * @param date the date
     * @return the Julian day number
     */
    private static int julianDay(LocalDate date) {
        // Gregorian throughout, like LocalDate: a Julian branch before 1582 read one calendar's fields as the other's.
        int dd = date.getDayOfMonth();
        int mm = date.getMonthValue();
        int yy = date.getYear();

        int a = (14 - mm) / 12;
        int y = yy + 4800 - a;
        int m = mm + 12 * a - 3;
        return dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
    }

    /**
     * Converts a Julian day number back to a proleptic Gregorian date.
     *
     * @param jd the Julian day number
     * @return the date
     */
    private static LocalDate fromJulianDay(int jd) {
        int a = jd + 32044;
        int b = (4 * a + 3) / 146097;
        int c = a - (b * 146097) / 4;
        int d = (4 * c + 3) / 1461;
        int e = c - (1461 * d) / 4;
        int m = (5 * e + 2) / 153;

        int day = e - (153 * m + 2) / 5 + 1;
        int month = m + 3 - 12 * (m / 10);
        int year = b * 100 + d - 4800 + m / 10;
        return LocalDate.of(year, month, day);
    }

    /**
     * Returns the Julian day of the k-th new moon since the epoch.
     *
     * @param k the lunation index
     * @return the Julian day, with a fractional part
     */
    private static double newMoon(int k) {
        double t = k / 1236.85;
        double t2 = t * t;
        double t3 = t2 * t;

        double jd = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3;
        jd += 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * DEG);

        double sunMeanAnomaly = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3;
        double moonMeanAnomaly = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3;
        double moonArgument = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3;

        double correction = (0.1734 - 0.000393 * t) * Math.sin(sunMeanAnomaly * DEG)
                + 0.0021 * Math.sin(2 * DEG * sunMeanAnomaly)
                - 0.4068 * Math.sin(moonMeanAnomaly * DEG)
                + 0.0161 * Math.sin(DEG * 2 * moonMeanAnomaly)
                - 0.0004 * Math.sin(DEG * 3 * moonMeanAnomaly)
                + 0.0104 * Math.sin(DEG * 2 * moonArgument)
                - 0.0051 * Math.sin(DEG * (sunMeanAnomaly + moonMeanAnomaly))
                - 0.0074 * Math.sin(DEG * (sunMeanAnomaly - moonMeanAnomaly))
                + 0.0004 * Math.sin(DEG * (2 * moonArgument + sunMeanAnomaly))
                - 0.0004 * Math.sin(DEG * (2 * moonArgument - sunMeanAnomaly))
                - 0.0006 * Math.sin(DEG * (2 * moonArgument + moonMeanAnomaly))
                + 0.0010 * Math.sin(DEG * (2 * moonArgument - moonMeanAnomaly))
                + 0.0005 * Math.sin(DEG * (2 * moonMeanAnomaly + sunMeanAnomaly));

        double deltaT = t < -11
                ? 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
                : -0.000278 + 0.000265 * t + 0.000262 * t2;

        return jd + correction - deltaT;
    }

    /**
     * Returns the sun's apparent longitude at a Julian day, in radians.
     *
     * @param jd the Julian day
     * @return the longitude in radians, 0..2π
     */
    private static double sunLongitude(double jd) {
        double t = (jd - 2451545.0) / 36525;
        double t2 = t * t;

        double meanAnomaly = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2;
        double meanLongitude = 280.46645 + 36000.76983 * t + 0.0003032 * t2;

        double equationOfCentre = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(DEG * meanAnomaly)
                + (0.019993 - 0.000101 * t) * Math.sin(DEG * 2 * meanAnomaly)
                + 0.000290 * Math.sin(DEG * 3 * meanAnomaly);

        double omega = 125.04 - 1934.136 * t;
        double longitude = meanLongitude + equationOfCentre - 0.00569 - 0.00478 * Math.sin(omega * DEG);

        longitude = longitude * DEG;
        return longitude - Math.PI * 2 * Math.floor(longitude / (Math.PI * 2));
    }

    /**
     * Returns which 30-degree sector of the ecliptic the sun occupies on a day.
     *
     * @param dayNumber the Julian day number
     * @param timeZone the offset in hours
     * @return the sector, 0..11
     */
    private static int sunSector(int dayNumber, double timeZone) {
        return (int) Math.floor(sunLongitude(dayNumber - 0.5 - timeZone / 24) / Math.PI * 6);
    }

    /**
     * Returns the Julian day number on which the k-th new moon falls locally.
     *
     * @param k the lunation index
     * @param timeZone the offset in hours
     * @return the local Julian day number
     */
    private static int newMoonDay(int k, double timeZone) {
        // The only line the time zone changes: at UTC+8 a new moon just before midnight lands a day later.
        return (int) Math.floor(newMoon(k) + 0.5 + timeZone / 24);
    }

    /**
     * Returns the Julian day of the start of the 11th lunar month of a Gregorian year.
     *
     * @param year the Gregorian year
     * @param timeZone the offset in hours
     * @return the Julian day number of that month's first day
     */
    private static int lunarMonth11(int year, double timeZone) {
        double offset = julianDay(LocalDate.of(year, 12, 31)) - EPOCH_NEW_MOON;
        int k = (int) Math.floor(offset / SYNODIC_MONTH);
        int newMoonDay = newMoonDay(k, timeZone);
        if (sunSector(newMoonDay, timeZone) >= 9) {
            newMoonDay = newMoonDay(k - 1, timeZone);
        }
        return newMoonDay;
    }

    /**
     * Returns how many months after the 11th month the leap month falls, in a leap lunar year.
     *
     * @param month11 the Julian day of the 11th month's first day
     * @param timeZone the offset in hours
     * @return the offset of the leap month
     */
    private static int leapMonthOffset(int month11, double timeZone) {
        int k = (int) Math.floor((month11 - EPOCH_NEW_MOON) / SYNODIC_MONTH + 0.5);
        int i = 1;
        int previous;
        int sector = sunSector(newMoonDay(k + i, timeZone), timeZone);
        do {
            previous = sector;
            i++;
            sector = sunSector(newMoonDay(k + i, timeZone), timeZone);
        } while (sector != previous && i < 14);
        return i - 1;
    }
}
