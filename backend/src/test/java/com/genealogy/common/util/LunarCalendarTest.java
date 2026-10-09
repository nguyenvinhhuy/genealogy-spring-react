package com.genealogy.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for the Vietnamese lunar conversion (CLAUDE.md §4.2). */
class LunarCalendarTest {

    @ParameterizedTest(name = "{0} is mùng 1 Tết {1}")
    @DisplayName("known Tết dates convert to mùng 1 tháng 1, on both sides of the 1968 switch")
    @CsvSource({
        // Before 1968 at UTC+8, the offset âm lịch was computed at (§8.10 D2).
        "1900-01-31, 1900",
        "1945-02-13, 1945",
        "1954-02-03, 1954",
        "1966-01-21, 1966",
        "1967-02-09, 1967",
        // Tết Mậu Thân: the North, now at UTC+7, kept it on the 29th; the South, still UTC+8, on the 30th.
        "1968-01-29, 1968",
        "1985-01-21, 1985",
        "2021-02-12, 2021",
        "2022-02-01, 2022",
        "2023-01-22, 2023",
        "2024-02-10, 2024",
        "2025-01-29, 2025",
        "2026-02-17, 2026",
    })
    void knownTetDates(String solar, int year) {
        LunarDate lunar = LunarCalendar.toLunar(LocalDate.parse(solar));

        assertThat(lunar).isEqualTo(LunarDate.of(1, 1, year));
        assertThat(LunarCalendar.toSolar(LunarDate.of(1, 1, year))).contains(LocalDate.parse(solar));
    }

    @Test
    @DisplayName("the day before Tết Mậu Thân in the North is still the old year, not Tết itself")
    void theSwitchIsTheNorthernOne() {
        // At UTC+8 the new moon falls on the 30th, so reading 1968 at UTC+8 would put Tết a day late.
        assertThat(LunarCalendar.toLunar(LocalDate.of(1968, 1, 28)).year()).isEqualTo(1967);
    }

    @ParameterizedTest(name = "{0} is day {1} of the leap month {2} of {3}")
    @DisplayName("known leap months are pinned, not found by asking the code under test")
    @CsvSource({
        // Quý Mão 2023: tháng 2 nhuận began on 22 March.
        "2023-03-22, 1, 2, 2023",
        // Canh Tý 2020: tháng 4 nhuận began on 23 May.
        "2020-05-23, 1, 4, 2020",
    })
    void knownLeapMonths(String solar, int day, int month, int year) {
        LunarDate leap = new LunarDate(day, month, year, true);

        assertThat(LunarCalendar.toLunar(LocalDate.parse(solar))).isEqualTo(leap);
        assertThat(LunarCalendar.toSolar(leap)).contains(LocalDate.parse(solar));
        // The ordinary month of the same number is a whole month earlier.
        assertThat(LunarCalendar.toSolar(LunarDate.of(day, month, year)).orElseThrow())
                .isBefore(LocalDate.parse(solar).minusDays(28));
    }

    @Test
    @DisplayName("asking for a leap month the year does not have returns nothing, even in a leap year")
    void leapMonthThatIsNotThere() {
        // 2024 has no leap month at all; 2023 has one, but it is tháng 2, not tháng 5.
        assertThat(LunarCalendar.toSolar(new LunarDate(1, 5, 2024, true))).isEmpty();
        assertThat(LunarCalendar.toSolar(new LunarDate(1, 5, 2023, true))).isEmpty();
    }

    @Test
    @DisplayName("every day from 1900 to 2040 round-trips back to itself, across the 1968 switch")
    void roundTripsAcrossTheTwentiethCentury() {
        LocalDate date = LocalDate.of(1900, 1, 1);
        LocalDate end = LocalDate.of(2040, 1, 1);

        while (date.isBefore(end)) {
            LunarDate lunar = LunarCalendar.toLunar(date);
            Optional<LocalDate> back = LunarCalendar.toSolar(lunar);

            assertThat(back).as("round trip of %s via %s", date, lunar).contains(date);
            date = date.plusDays(1);
        }
    }

    @Test
    @DisplayName("a date before 1582 converts without throwing and stays in order")
    void datesBeforeTheGregorianReformAreGregorian() {
        // The Julian branch read Julian fields into a Gregorian LocalDate; 29 February 1500 is Julian-only.
        LocalDate date = LocalDate.of(1499, 12, 1);
        LocalDate end = LocalDate.of(1500, 4, 1);
        LunarDate previous = null;
        while (date.isBefore(end)) {
            LunarDate lunar = LunarCalendar.toLunar(date);
            assertThat(LunarCalendar.toSolar(lunar)).as("round trip of %s", date).contains(date);
            if (previous != null && previous.month() == lunar.month() && previous.leapMonth() == lunar.leapMonth()) {
                assertThat(lunar.day()).as("day after %s", previous).isEqualTo(previous.day() + 1);
            }
            previous = lunar;
            date = date.plusDays(1);
        }
    }

    @Test
    @DisplayName("UTC+8 really does disagree with UTC+7 after 1968, which is why no Chinese lunar library is used")
    void chineseOffsetDisagrees() {
        LocalDate date = LocalDate.of(2000, 1, 1);
        LocalDate end = LocalDate.of(2030, 1, 1);
        int disagreements = 0;

        while (date.isBefore(end)) {
            LunarDate vietnamese = LunarCalendar.toLunar(date, 7.0);
            LunarDate chinese = LunarCalendar.toLunar(date, 8.0);
            if (!vietnamese.equals(chinese)) {
                disagreements++;
            }
            date = date.plusDays(1);
        }

        // If this ever hits zero the §3.3 rule has become pointless - and that would itself be news.
        assertThat(disagreements)
                .as("days where Vietnamese and Chinese âm lịch differ, 2000-2030")
                .isPositive();
    }

    @ParameterizedTest(name = "{0} is năm {1}")
    @DisplayName("a lunar year is named by its can-chi")
    @CsvSource({
        "1984, Giáp Tý",
        "1950, Canh Dần",
        "1968, Mậu Thân",
        "2023, Quý Mão",
        "2026, Bính Ngọ",
    })
    void canChi(int year, String name) {
        assertThat(LunarCalendar.canChi(year)).isEqualTo(name);
    }

    @Test
    @DisplayName("a giỗ falls about eleven days earlier each Gregorian year")
    void anniversaryDriftsBackwards() {
        LocalDate first = LunarCalendar.nextAnniversary(10, 3, LocalDate.of(2026, 1, 1));
        LocalDate second = LunarCalendar.nextAnniversary(10, 3, first.plusDays(1));

        assertThat(LunarCalendar.toLunar(first)).isEqualTo(LunarDate.of(10, 3, 2026));
        assertThat(LunarCalendar.toLunar(second)).isEqualTo(LunarDate.of(10, 3, 2027));

        long gap = second.toEpochDay() - first.toEpochDay();
        // A lunar year is ~354 days, or ~384 when the year carries a leap month.
        assertThat(gap).isBetween(350L, 390L);
    }

    @Test
    @DisplayName("the next anniversary is never before the date asked from")
    void anniversaryIsNeverInThePast() {
        LocalDate from = LocalDate.of(2026, 6, 1);

        for (int month = 1; month <= 12; month++) {
            LocalDate next = LunarCalendar.nextAnniversary(15, month, from);
            assertThat(next).as("anniversary of 15/%d from %s", month, from).isAfterOrEqualTo(from);
        }
    }

    @Test
    @DisplayName("a leap-month giỗ is kept in the ordinary month, even in a year that repeats it")
    void leapMonthGioIsInTheOrdinaryMonth() {
        // 2023 repeats tháng 2; the custom keeps the giỗ in the first, ordinary tháng 2.
        LocalDate next = LunarCalendar.nextAnniversary(10, 2, LocalDate.of(2023, 1, 22));

        assertThat(LunarCalendar.toLunar(next)).isEqualTo(LunarDate.of(10, 2, 2023));
    }

    @Test
    @DisplayName("a 30th keeps the 30th in a 30-day month and falls back to the 29th only in a 29-day one")
    void thirtiethFallsBackOnlyWhenMissing() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        for (int month = 1; month <= 12; month++) {
            LocalDate next = LunarCalendar.nextAnniversary(30, month, from);
            LunarDate lunar = LunarCalendar.toLunar(next);
            // Whether this month has a 30th is read from the calendar itself: day 29 plus one is still the month.
            LocalDate twentyNinth = LunarCalendar.toSolar(LunarDate.of(29, month, lunar.year())).orElseThrow();
            boolean hasThirtieth = LunarCalendar.toLunar(twentyNinth.plusDays(1)).day() == 30;

            assertThat(lunar.month()).as("month of the 30th/%d anniversary", month).isEqualTo(month);
            assertThat(lunar.leapMonth()).as("30th/%d lands in the ordinary month", month).isFalse();
            assertThat(lunar.day()).as("day of the 30th/%d anniversary", month).isEqualTo(hasThirtieth ? 30 : 29);
            // Within roughly one lunar year, not pushed out to the year after.
            assertThat(next).isBefore(from.plusDays(400));
        }
    }
}
