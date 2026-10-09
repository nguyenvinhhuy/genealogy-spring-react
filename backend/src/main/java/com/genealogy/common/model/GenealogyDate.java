package com.genealogy.common.model;

import com.genealogy.common.util.LunarCalendar;
import com.genealogy.common.util.LunarDate;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A genealogical date, routinely partial or approximate; never a bare {@code LocalDate} (CLAUDE.md §3.2). */
@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class GenealogyDate implements Serializable {

    private static final long serialVersionUID = 1L;

    // "khoảng 1890" is a family's estimate, so a consistency check widens it this far before calling it impossible.
    private static final int APPROXIMATE_SLACK_YEARS = 5;
    private static final int LUNAR_MONTH_MAX_DAYS = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_modifier", nullable = false, length = 20)
    private DateModifier modifier = DateModifier.EXACT;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_calendar", nullable = false, length = 10)
    private CalendarType calendar = CalendarType.SOLAR;

    @Column(name = "date_year")
    private Integer year;

    @Column(name = "date_month")
    private Integer month;

    @Column(name = "date_day")
    private Integer day;

    // Tháng nhuận: the repeated month of a lunar leap year, which §3.3 requires to be recorded explicitly.
    @Column(name = "date_leap_month", nullable = false)
    private boolean leapMonth;

    @Column(name = "date_year2")
    private Integer year2;

    @Column(name = "date_month2")
    private Integer month2;

    @Column(name = "date_day2")
    private Integer day2;

    // The second endpoint's tháng nhuận: a lunar range can end in the leap month as easily as begin in one.
    @Column(name = "date_leap_month2", nullable = false)
    private boolean leapMonth2;

    // Derived; for ordering and comparison only, never rendered to a user.
    @Column(name = "date_sort")
    private LocalDate sortDate;

    // Verbatim user input, kept so nothing the family typed is lost.
    @Column(name = "date_raw", columnDefinition = "text")
    private String raw;

    /** Recomputes {@link #getSortDate()} from the current parts, following the table in CLAUDE.md §3.2. */
    public void deriveSortDate() {
        // Services call this on write rather than a @PrePersist hook, so the rule stays readable and testable.
        this.sortDate = computeSortDate();
    }

    /**
     * Returns the earliest Gregorian day this date allows, for checks that must not fire on a merely vague date.
     *
     * @return that day, or null when the date sets no lower bound
     */
    public LocalDate earliestPossible() {
        if (year == null) {
            return null;
        }
        return switch (modifier) {
            case BEFORE -> null;
            case AFTER -> solarEnd(year, month, day, leapMonth).plusDays(1);
            case ABOUT, ESTIMATED, CALCULATED ->
                    solarStart(year, month, day, leapMonth).minusYears(APPROXIMATE_SLACK_YEARS);
            case EXACT, BETWEEN -> solarStart(year, month, day, leapMonth);
        };
    }

    /**
     * Returns the latest Gregorian day this date allows, for checks that must not fire on a merely vague date.
     *
     * @return that day, or null when the date sets no upper bound
     */
    public LocalDate latestPossible() {
        if (year == null) {
            return null;
        }
        return switch (modifier) {
            case BEFORE -> solarStart(year, month, day, leapMonth).minusDays(1);
            case AFTER -> null;
            case ABOUT, ESTIMATED, CALCULATED ->
                    solarEnd(year, month, day, leapMonth).plusYears(APPROXIMATE_SLACK_YEARS);
            case BETWEEN -> year2 == null ? null : solarEnd(year2, month2, day2, leapMonth2);
            case EXACT -> solarEnd(year, month, day, leapMonth);
        };
    }

    /**
     * Resolves a possibly-partial date in this date's calendar to the Gregorian first day of its period.
     *
     * @param y year, never null
     * @param m month, or null
     * @param d day, or null
     * @param leap whether the month is a lunar leap month
     * @return the Gregorian first day
     */
    private LocalDate solarStart(Integer y, Integer m, Integer d, boolean leap) {
        if (calendar == CalendarType.SOLAR) {
            return startOfPeriod(y, m, d);
        }
        return lunarToSolar(y, m == null ? 1 : m, d == null ? 1 : d, m != null && leap);
    }

    /**
     * Resolves a possibly-partial date in this date's calendar to the Gregorian last day of its period.
     *
     * @param y year, never null
     * @param m month, or null
     * @param d day, or null
     * @param leap whether the month is a lunar leap month
     * @return the Gregorian last day
     */
    private LocalDate solarEnd(Integer y, Integer m, Integer d, boolean leap) {
        if (calendar == CalendarType.SOLAR) {
            return endOfPeriod(y, m, d);
        }
        if (m == null) {
            return lunarToSolar(y + 1, 1, 1, false).minusDays(1);
        }
        if (d != null) {
            return lunarToSolar(y, m, d, leap);
        }
        // A lunar month has 29 or 30 days; the 30th exists only if the day 29 after its first is still that month.
        LocalDate first = lunarToSolar(y, m, 1, leap);
        LocalDate thirtieth = first.plusDays(LUNAR_MONTH_MAX_DAYS - 1);
        return LunarCalendar.toLunar(thirtieth).day() == LUNAR_MONTH_MAX_DAYS ? thirtieth : thirtieth.minusDays(1);
    }

    /**
     * Computes the sortable instant this date should order by.
     *
     * @return the derived date, or null when no year is known
     */
    private LocalDate computeSortDate() {
        if (year == null) {
            return null;
        }
        return switch (modifier) {
            // A range sorts at its centre so it lands among the dates it overlaps.
            case BETWEEN -> year2 == null
                    ? solarStart(year, month, day, leapMonth)
                    : midpoint(solarStart(year, month, day, leapMonth), solarEnd(year2, month2, day2, leapMonth2));
            // BEFORE/AFTER name their own bound, so that bound is the only defensible sort key.
            case BEFORE, AFTER, EXACT, ABOUT, ESTIMATED, CALCULATED -> solarStart(year, month, day, leapMonth);
        };
    }

    /**
     * Converts a lunar day to the Gregorian day it fell on.
     *
     * @param y lunar year
     * @param m lunar month
     * @param d lunar day, up to 30
     * @param leap whether the month is the leap month
     * @return the Gregorian day
     */
    private static LocalDate lunarToSolar(int y, int m, int d, boolean leap) {
        // Clamped to a lunar month's 30, not a Gregorian one's: lunar 30/2 is a real giỗ, not 28 February.
        int lunarDay = Math.min(d, LUNAR_MONTH_MAX_DAYS);
        // A leap flag on a year with no such leap month is a slip at entry; the ordinary month, always there, is meant.
        LocalDate solar = LunarCalendar.toSolar(new LunarDate(lunarDay, m, y, leap))
                .orElseGet(() -> LunarCalendar.toSolar(new LunarDate(lunarDay, m, y, false)).orElseThrow());
        // §3.3: day 30 of a 29-day month falls back to day 29 rather than spilling into the next month.
        return lunarDay == LUNAR_MONTH_MAX_DAYS && LunarCalendar.toLunar(solar).day() != LUNAR_MONTH_MAX_DAYS
                ? solar.minusDays(1)
                : solar;
    }

    /**
     * Resolves a possibly-partial date to the first day of the period it names.
     *
     * @param y year, never null
     * @param m month, or null when only the year is known
     * @param d day, or null when only year and month are known
     * @return the first day of that year, month or day
     */
    private static LocalDate startOfPeriod(Integer y, Integer m, Integer d) {
        if (m == null) {
            return LocalDate.of(y, 1, 1);
        }
        if (d == null) {
            return LocalDate.of(y, m, 1);
        }
        return LocalDate.of(y, m, clampDay(y, m, d));
    }

    /**
     * Resolves a possibly-partial date to the last day of the period it names.
     *
     * @param y year, never null
     * @param m month, or null when only the year is known
     * @param d day, or null when only year and month are known
     * @return the last day of that year, month or day
     */
    private static LocalDate endOfPeriod(Integer y, Integer m, Integer d) {
        if (m == null) {
            return LocalDate.of(y, 12, 31);
        }
        if (d == null) {
            return YearMonth.of(y, m).atEndOfMonth();
        }
        return LocalDate.of(y, m, clampDay(y, m, d));
    }

    /**
     * Clamps a day number to the length of its month.
     *
     * @param y year
     * @param m month
     * @param d day as recorded
     * @return a day number that exists in that month
     */
    private static int clampDay(int y, int m, int d) {
        // Old records do contain impossible days (31 February); clamping keeps the row sortable, raw kept.
        return Math.min(d, YearMonth.of(y, m).lengthOfMonth());
    }

    /**
     * Returns the day halfway between two dates.
     *
     * @param from the earlier endpoint
     * @param to the later endpoint
     * @return the midpoint, rounded down
     */
    private static LocalDate midpoint(LocalDate from, LocalDate to) {
        return from.plusDays((to.toEpochDay() - from.toEpochDay()) / 2);
    }
}
