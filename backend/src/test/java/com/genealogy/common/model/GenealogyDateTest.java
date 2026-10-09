package com.genealogy.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.common.util.LunarCalendar;
import com.genealogy.common.util.LunarDate;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link GenealogyDate}'s sort-date derivation (CLAUDE.md 4.2). */
class GenealogyDateTest {

    /**
     * Builds a date with the given parts.
     *
     * @param modifier the precision modifier
     * @param y year
     * @param m month, may be null
     * @param d day, may be null
     * @return the date, with its sort date already derived
     */
    private static GenealogyDate date(DateModifier modifier, Integer y, Integer m, Integer d) {
        GenealogyDate value = new GenealogyDate();
        value.setModifier(modifier);
        value.setYear(y);
        value.setMonth(m);
        value.setDay(d);
        value.deriveSortDate();
        return value;
    }

    @Test
    @DisplayName("a full exact date sorts at itself")
    void exactFullDate() {
        assertThat(date(DateModifier.EXACT, 1890, 3, 15).getSortDate()).isEqualTo(LocalDate.of(1890, 3, 15));
    }

    @Test
    @DisplayName("a year-only date sorts at 1 January of that year")
    void yearOnly() {
        assertThat(date(DateModifier.EXACT, 1890, null, null).getSortDate()).isEqualTo(LocalDate.of(1890, 1, 1));
    }

    @Test
    @DisplayName("a year-and-month date sorts at the first of that month")
    void yearAndMonth() {
        assertThat(date(DateModifier.EXACT, 1890, 3, null).getSortDate()).isEqualTo(LocalDate.of(1890, 3, 1));
    }

    @Test
    @DisplayName("ABOUT, ESTIMATED and CALCULATED sort like EXACT")
    void approximateModifiersSortLikeExact() {
        LocalDate expected = LocalDate.of(1890, 1, 1);
        assertThat(date(DateModifier.ABOUT, 1890, null, null).getSortDate()).isEqualTo(expected);
        assertThat(date(DateModifier.ESTIMATED, 1890, null, null).getSortDate()).isEqualTo(expected);
        assertThat(date(DateModifier.CALCULATED, 1890, null, null).getSortDate()).isEqualTo(expected);
    }

    @Test
    @DisplayName("BEFORE and AFTER sort at the bound they name")
    void beforeAndAfterSortAtTheirBound() {
        assertThat(date(DateModifier.BEFORE, 1900, null, null).getSortDate()).isEqualTo(LocalDate.of(1900, 1, 1));
        assertThat(date(DateModifier.AFTER, 1900, null, null).getSortDate()).isEqualTo(LocalDate.of(1900, 1, 1));
    }

    @Test
    @DisplayName("BETWEEN sorts at the midpoint of the whole range")
    void betweenSortsAtMidpoint() {
        GenealogyDate value = new GenealogyDate();
        value.setModifier(DateModifier.BETWEEN);
        value.setYear(1918);
        value.setYear2(1922);
        value.deriveSortDate();

        // 1918-01-01 .. 1922-12-31 spans 1825 days, so the midpoint is 912 days in: 1920-07-01.
        assertThat(value.getSortDate()).isEqualTo(LocalDate.of(1920, 7, 1));
    }

    @Test
    @DisplayName("BETWEEN with full endpoints sorts between them")
    void betweenWithFullEndpoints() {
        GenealogyDate value = new GenealogyDate();
        value.setModifier(DateModifier.BETWEEN);
        value.setYear(1920);
        value.setMonth(1);
        value.setDay(1);
        value.setYear2(1920);
        value.setMonth2(1);
        value.setDay2(11);
        value.deriveSortDate();

        assertThat(value.getSortDate()).isEqualTo(LocalDate.of(1920, 1, 6));
    }

    @Test
    @DisplayName("an impossible day is clamped to the end of its month rather than throwing")
    void impossibleDayIsClamped() {
        assertThat(date(DateModifier.EXACT, 1890, 2, 31).getSortDate()).isEqualTo(LocalDate.of(1890, 2, 28));
    }

    @Test
    @DisplayName("29 February is kept in a leap year")
    void leapDayIsPreserved() {
        assertThat(date(DateModifier.EXACT, 1920, 2, 29).getSortDate()).isEqualTo(LocalDate.of(1920, 2, 29));
    }

    @Test
    @DisplayName("a lunar date sorts at its Gregorian equivalent, not at its lunar numbers")
    void lunarDateSortsAtItsSolarEquivalent() {
        GenealogyDate lunar = new GenealogyDate();
        lunar.setCalendar(CalendarType.LUNAR);
        lunar.setYear(1950);
        lunar.setMonth(3);
        lunar.setDay(10);
        lunar.deriveSortDate();

        // Taken literally it would be 1950-03-10, about a month adrift from where the giỗ really sorts.
        assertThat(lunar.getSortDate()).isNotEqualTo(LocalDate.of(1950, 3, 10));
        assertThat(lunar.getSortDate()).isEqualTo(LunarCalendar.toSolar(LunarDate.of(10, 3, 1950)).orElseThrow());
    }

    @Test
    @DisplayName("a solar date is left exactly as written")
    void solarDateIsNotConverted() {
        GenealogyDate solar = new GenealogyDate();
        solar.setCalendar(CalendarType.SOLAR);
        solar.setYear(1950);
        solar.setMonth(3);
        solar.setDay(10);
        solar.deriveSortDate();

        assertThat(solar.getSortDate()).isEqualTo(LocalDate.of(1950, 3, 10));
    }

    @Test
    @DisplayName("a date with no year at all derives no sort date")
    void noYearMeansNoSortDate() {
        GenealogyDate value = new GenealogyDate();
        value.deriveSortDate();

        assertThat(value.getSortDate()).isNull();
    }

    @Test
    @DisplayName("a lunar giỗ with a day and month but no year is a valid date with no sort date and no bounds")
    void lunarDayAndMonthWithoutAYear() {
        GenealogyDate value = new GenealogyDate();
        value.setCalendar(CalendarType.LUNAR);
        value.setMonth(3);
        value.setDay(12);
        value.deriveSortDate();

        // §3.2's "no year at all" row: nothing to sort by, and nothing a consistency rule can compare.
        assertThat(value.getSortDate()).isNull();
        assertThat(value.earliestPossible()).isNull();
        assertThat(value.latestPossible()).isNull();
    }

    @Test
    @DisplayName("an exact year-only date allows the whole of that year")
    void yearOnlySpansTheYear() {
        GenealogyDate value = date(DateModifier.EXACT, 1945, null, null);

        assertThat(value.earliestPossible()).isEqualTo(LocalDate.of(1945, 1, 1));
        assertThat(value.latestPossible()).isEqualTo(LocalDate.of(1945, 12, 31));
    }

    @Test
    @DisplayName("an exact full date allows only itself")
    void fullDateSpansOneDay() {
        GenealogyDate value = date(DateModifier.EXACT, 1945, 6, 15);

        assertThat(value.earliestPossible()).isEqualTo(LocalDate.of(1945, 6, 15));
        assertThat(value.latestPossible()).isEqualTo(LocalDate.of(1945, 6, 15));
    }

    @Test
    @DisplayName("BEFORE sets only an upper bound and AFTER only a lower one, both excluding the year named")
    void beforeAndAfterAreOpenEnded() {
        GenealogyDate before = date(DateModifier.BEFORE, 1900, null, null);
        GenealogyDate after = date(DateModifier.AFTER, 1950, null, null);

        assertThat(before.earliestPossible()).isNull();
        assertThat(before.latestPossible()).isEqualTo(LocalDate.of(1899, 12, 31));
        assertThat(after.earliestPossible()).isEqualTo(LocalDate.of(1951, 1, 1));
        assertThat(after.latestPossible()).isNull();
    }

    @Test
    @DisplayName("BETWEEN allows its whole range, not its midpoint")
    void betweenSpansTheRange() {
        GenealogyDate value = new GenealogyDate();
        value.setModifier(DateModifier.BETWEEN);
        value.setYear(1918);
        value.setYear2(1922);

        assertThat(value.earliestPossible()).isEqualTo(LocalDate.of(1918, 1, 1));
        assertThat(value.latestPossible()).isEqualTo(LocalDate.of(1922, 12, 31));
    }

    @Test
    @DisplayName("ABOUT is widened, so an estimate is never called impossible by a year or two")
    void aboutIsWidened() {
        GenealogyDate value = date(DateModifier.ABOUT, 1890, null, null);

        assertThat(value.earliestPossible()).isBefore(LocalDate.of(1888, 1, 1));
        assertThat(value.latestPossible()).isAfter(LocalDate.of(1892, 12, 31));
    }

    @Test
    @DisplayName("a lunar year-only date spans Tết to the eve of the next Tết, in Gregorian days")
    void lunarYearSpansTetToTet() {
        GenealogyDate lunar = new GenealogyDate();
        lunar.setCalendar(CalendarType.LUNAR);
        lunar.setYear(1950);

        LocalDate tet1950 = LunarCalendar.toSolar(LunarDate.of(1, 1, 1950)).orElseThrow();
        LocalDate tet1951 = LunarCalendar.toSolar(LunarDate.of(1, 1, 1951)).orElseThrow();
        assertThat(lunar.earliestPossible()).isEqualTo(tet1950);
        assertThat(lunar.latestPossible()).isEqualTo(tet1951.minusDays(1));
    }

    @Test
    @DisplayName("a day in a tháng nhuận sorts a month after the same day of the ordinary month")
    void leapMonthSortsAfterTheOrdinaryMonth() {
        GenealogyDate ordinary = lunar(2023, 2, 1, false);
        GenealogyDate repeat = lunar(2023, 2, 1, true);

        // Pinned, not looked up: Quý Mão's tháng 2 nhuận began on 22 March 2023, the ordinary one on 20 February.
        assertThat(repeat.getSortDate()).isEqualTo(LocalDate.of(2023, 3, 22));
        assertThat(ordinary.getSortDate()).isEqualTo(LocalDate.of(2023, 2, 20));
    }

    @Test
    @DisplayName("a leap flag on a month the year does not repeat falls back to that ordinary month")
    void leapFlagWithoutThatLeapMonthIsTheOrdinaryMonth() {
        // 2024 has no leap month at all; 2023 has one, but it is tháng 2, not tháng 5.
        assertThat(lunar(2024, 5, 10, true).getSortDate())
                .isEqualTo(LunarCalendar.toSolar(LunarDate.of(10, 5, 2024)).orElseThrow());
        assertThat(lunar(2023, 5, 10, true).getSortDate())
                .isEqualTo(LunarCalendar.toSolar(LunarDate.of(10, 5, 2023)).orElseThrow());
    }

    @Test
    @DisplayName("a lunar range ending in the leap month ends there, not a month early")
    void rangeEndingInTheLeapMonth() {
        GenealogyDate value = new GenealogyDate();
        value.setModifier(DateModifier.BETWEEN);
        value.setCalendar(CalendarType.LUNAR);
        value.setYear(2023);
        value.setMonth(1);
        value.setDay(1);
        value.setYear2(2023);
        value.setMonth2(2);
        value.setDay2(15);
        value.setLeapMonth2(true);

        // 15 of the leap tháng 2 of 2023 is 5 April; the ordinary one was 6 March.
        assertThat(value.latestPossible()).isEqualTo(LocalDate.of(2023, 4, 5));
    }

    @Test
    @DisplayName("a lunar date from the 1400s derives a sort date instead of throwing")
    void lunarDateBeforeTheGregorianReform() {
        // Lunar dates near Julian 29 February 1500 used to reach LocalDate.of(1500, 2, 29) and fail the save.
        for (int day = 1; day <= 29; day++) {
            assertThat(lunar(1500, 2, day, false).getSortDate()).as("lunar %d/2/1500", day).isNotNull();
        }
    }

    @Test
    @DisplayName("day 30 of a 29-day lunar month falls back to day 29, not into the next month (§3.3)")
    void day30OfAShortMonthIsDay29() {
        int shortMonth = shortMonthOf(2024);

        assertThat(lunar(2024, shortMonth, 30, false).getSortDate())
                .isEqualTo(LunarCalendar.toSolar(LunarDate.of(29, shortMonth, 2024)).orElseThrow());
    }

    /**
     * Builds a full lunar date with its sort date derived.
     *
     * @param y lunar year
     * @param m lunar month
     * @param d lunar day
     * @param leap whether the month is the leap month
     * @return the date
     */
    private static GenealogyDate lunar(int y, int m, int d, boolean leap) {
        GenealogyDate value = new GenealogyDate();
        value.setCalendar(CalendarType.LUNAR);
        value.setYear(y);
        value.setMonth(m);
        value.setDay(d);
        value.setLeapMonth(leap);
        value.deriveSortDate();
        return value;
    }

    /**
     * Finds a month of a lunar year that has only 29 days.
     *
     * @param year the lunar year
     * @return the first such month
     */
    private static int shortMonthOf(int year) {
        for (int month = 1; month <= 12; month++) {
            LocalDate day30 = LunarCalendar.toSolar(LunarDate.of(30, month, year)).orElseThrow();
            if (LunarCalendar.toLunar(day30).day() != 30) {
                return month;
            }
        }
        throw new IllegalStateException(year + " has no 29-day month");
    }

    @Test
    @DisplayName("a date with no year allows anything")
    void noYearHasNoBounds() {
        GenealogyDate value = new GenealogyDate();

        assertThat(value.earliestPossible()).isNull();
        assertThat(value.latestPossible()).isNull();
    }

    @Test
    @DisplayName("ordering a mixed set puts them in the order a reader would expect")
    void mixedDatesOrderSensibly() {
        LocalDate about1890 = date(DateModifier.ABOUT, 1890, null, null).getSortDate();
        LocalDate exact1895 = date(DateModifier.EXACT, 1895, 6, 1).getSortDate();
        LocalDate before1900 = date(DateModifier.BEFORE, 1900, null, null).getSortDate();

        assertThat(about1890).isBefore(exact1895);
        assertThat(exact1895).isBefore(before1900);
    }
}
