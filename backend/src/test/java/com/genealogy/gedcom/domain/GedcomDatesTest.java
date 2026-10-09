package com.genealogy.gedcom.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.common.model.CalendarType;
import com.genealogy.common.model.DateModifier;
import com.genealogy.common.util.LunarCalendar;
import com.genealogy.common.util.LunarDate;
import com.genealogy.event.dto.request.GenealogyDateRequest;
import com.genealogy.event.dto.response.GenealogyDateResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for converting a fuzzy date to and from a GEDCOM DATE payload. */
class GedcomDatesTest {

    /**
     * Builds a solar date response with the given parts.
     *
     * @param modifier the precision modifier
     * @param y year
     * @param m month, may be null
     * @param d day, may be null
     * @return the date
     */
    private static GenealogyDateResponse solar(DateModifier modifier, Integer y, Integer m, Integer d) {
        return new GenealogyDateResponse(
                modifier, CalendarType.SOLAR, y, m, d, null, null, null, null, null, false, false);
    }

    /**
     * Builds a lunar date response with the given parts.
     *
     * @param modifier the precision modifier
     * @param y lunar year
     * @param m lunar month, may be null
     * @param d lunar day, may be null
     * @param raw the text the family typed, may be null
     * @return the date
     */
    private static GenealogyDateResponse lunar(
            DateModifier modifier, Integer y, Integer m, Integer d, String raw) {
        return new GenealogyDateResponse(
                modifier, CalendarType.LUNAR, y, m, d, null, null, null, raw, null, false, false);
    }

    @Test
    @DisplayName("a lunar date keeps its modifier through its own round trip")
    void lunarKeepsModifier() {
        GenealogyDateResponse giỗ = lunar(DateModifier.ABOUT, 1890, 3, 15, null);

        String payload = GedcomDates.format(giỗ);
        GenealogyDateRequest back = GedcomDates.parse(payload, GedcomDates.phrase(giỗ));

        assertThat(payload).startsWith("ABT ");
        assertThat(back).isNotNull();
        // Returned as EXACT, the record asserted a giỗ "khoảng 1890" as a hard fact (CLAUDE.md §3.2, §8 P5).
        assertThat(back.modifier()).isEqualTo(DateModifier.ABOUT);
        assertThat(back.calendar()).isEqualTo(CalendarType.LUNAR);
        assertThat(back.year()).isEqualTo(1890);
        assertThat(back.month()).isEqualTo(3);
        assertThat(back.day()).isEqualTo(15);
    }

    @Test
    @DisplayName("a lunar date keeps the words the family typed, beside the machine parts")
    void lunarKeepsRawText() {
        GenealogyDateResponse giỗ = lunar(DateModifier.EXACT, 1890, 3, 5, "mùng 5 tháng 3 âm");

        GenealogyDateRequest back = GedcomDates.parse(GedcomDates.format(giỗ), GedcomDates.phrase(giỗ));

        assertThat(back).isNotNull();
        assertThat(back.raw()).isEqualTo("mùng 5 tháng 3 âm");
    }

    @Test
    @DisplayName("a solar date whose raw text begins with the lunar marker stays solar")
    void solarRawBeginningWithLunarMarkerStaysSolar() {
        GenealogyDateResponse date = new GenealogyDateResponse(
                DateModifier.EXACT, CalendarType.SOLAR, 1990, 5, 3, null, null, null,
                "Âm lịch không rõ ngày", null, false, false);

        GenealogyDateRequest back = GedcomDates.parse(GedcomDates.format(date), GedcomDates.phrase(date));

        assertThat(back).isNotNull();
        // The whole date used to be discarded here, and with it the event that carried no description.
        assertThat(back.calendar()).isEqualTo(CalendarType.SOLAR);
        assertThat(back.year()).isEqualTo(1990);
    }

    @Test
    @DisplayName("FROM ... TO ... is a range, not a date after its own end")
    void readsFromToAsRange() {
        GenealogyDateRequest parsed = GedcomDates.parse("FROM 1918 TO 1922", null);

        assertThat(parsed).isNotNull();
        assertThat(parsed.modifier()).isEqualTo(DateModifier.BETWEEN);
        assertThat(parsed.year()).isEqualTo(1918);
        assertThat(parsed.year2()).isEqualTo(1922);
    }

    @Test
    @DisplayName("a bare TO names an upper bound, not the date itself")
    void readsBareToAsBefore() {
        GenealogyDateRequest parsed = GedcomDates.parse("TO 1922", null);

        assertThat(parsed).isNotNull();
        assertThat(parsed.modifier()).isEqualTo(DateModifier.BEFORE);
        assertThat(parsed.year()).isEqualTo(1922);
    }

    @Test
    @DisplayName("a range missing its upper bound does not become a BETWEEN with no end")
    void rangeWithoutUpperBound() {
        GenealogyDateRequest parsed = GedcomDates.parse("BET 1890 AND 92", null);

        assertThat(parsed).isNotNull();
        // A BETWEEN with a null year2 made deriveSortDate unbox null and killed the whole import with a 500.
        assertThat(parsed.modifier()).isEqualTo(DateModifier.AFTER);
        assertThat(parsed.year()).isEqualTo(1890);
        assertThat(parsed.year2()).isNull();
    }

    @Test
    @DisplayName("a malformed range keyword is refused rather than thrown out of")
    void malformedRangeIsRefused() {
        assertThat(GedcomDates.parse("BET AND 1900", null)).isNull();
    }

    @Test
    @DisplayName("each modifier renders as the GEDCOM keyword that means it")
    void rendersModifiers() {
        assertThat(GedcomDates.format(solar(DateModifier.EXACT, 1890, 3, 15))).isEqualTo("15 MAR 1890");
        assertThat(GedcomDates.format(solar(DateModifier.ABOUT, 1890, null, null))).isEqualTo("ABT 1890");
        assertThat(GedcomDates.format(solar(DateModifier.BEFORE, 1900, null, null))).isEqualTo("BEF 1900");
        assertThat(GedcomDates.format(solar(DateModifier.AFTER, 1900, null, null))).isEqualTo("AFT 1900");
        assertThat(GedcomDates.format(solar(DateModifier.ESTIMATED, 1890, null, null))).isEqualTo("EST 1890");
        assertThat(GedcomDates.format(solar(DateModifier.CALCULATED, 1890, null, null))).isEqualTo("CAL 1890");
    }

    @Test
    @DisplayName("a range renders as BET ... AND ...")
    void rendersRange() {
        GenealogyDateResponse between = new GenealogyDateResponse(
                DateModifier.BETWEEN, CalendarType.SOLAR, 1918, null, null, 1922, null, null, null, null, false, false);

        assertThat(GedcomDates.format(between)).isEqualTo("BET 1918 AND 1922");
    }

    @Test
    @DisplayName("a lunar giỗ with no year survives the round trip as day and month")
    void lunarDayAndMonthWithoutAYearRoundTrips() {
        GenealogyDateResponse gio = lunar(DateModifier.EXACT, null, 3, 12, null);

        // No Gregorian day exists to write, so the DATE is empty and the phrase carries the giỗ (§8.10 D1).
        assertThat(GedcomDates.format(gio)).isNull();
        GenealogyDateRequest back = GedcomDates.parse(null, GedcomDates.phrase(gio));

        assertThat(back).isNotNull();
        assertThat(back.calendar()).isEqualTo(CalendarType.LUNAR);
        assertThat(back.year()).isNull();
        assertThat(back.month()).isEqualTo(3);
        assertThat(back.day()).isEqualTo(12);
    }

    @Test
    @DisplayName("each endpoint of a lunar range keeps its own leap flag through the round trip")
    void lunarRangeKeepsEachLeapFlag() {
        GenealogyDateResponse range = new GenealogyDateResponse(
                DateModifier.BETWEEN, CalendarType.LUNAR, 2023, 1, 1, 2023, 2, 15, null, null, false, true);

        GenealogyDateRequest back = GedcomDates.parse(GedcomDates.format(range), GedcomDates.phrase(range));

        assertThat(back.leapMonth()).isFalse();
        assertThat(back.leapMonth2()).isTrue();
        assertThat(back.month2()).isEqualTo(2);
        assertThat(back.day2()).isEqualTo(15);
    }

    @Test
    @DisplayName("a year-only date renders as the bare year")
    void rendersYearOnly() {
        assertThat(GedcomDates.format(solar(DateModifier.EXACT, 1890, null, null))).isEqualTo("1890");
    }

    @Test
    @DisplayName("a date with no year at all renders as nothing")
    void rendersNothingWithoutYear() {
        assertThat(GedcomDates.format(solar(DateModifier.EXACT, null, null, null))).isNull();
        assertThat(GedcomDates.format(null)).isNull();
    }

    @Test
    @DisplayName("a lunar date exports as the Gregorian day it fell on, never as its lunar parts")
    void convertsLunarToGregorian() {
        GenealogyDateResponse lunar = lunar(DateModifier.EXACT, 1950, 3, 10, null);
        LocalDate expected = LunarCalendar.toSolar(LunarDate.of(10, 3, 1950)).orElseThrow();

        // Writing lunar parts as if they were Gregorian would move a ngày giỗ by weeks in every other program.
        assertThat(GedcomDates.format(lunar))
                .isEqualTo(expected.getDayOfMonth() + " " + monthOf(expected) + " " + expected.getYear())
                .isNotEqualTo("10 MAR 1950");
        assertThat(GedcomDates.phrase(lunar)).isEqualTo("Âm lịch 10 MAR 1950");
    }

    @Test
    @DisplayName("a lunar date survives a round trip through GEDCOM unchanged")
    void roundTripsLunar() {
        GenealogyDateResponse lunar = lunar(DateModifier.EXACT, 1950, 3, 10, null);

        GenealogyDateRequest back = GedcomDates.parse(GedcomDates.format(lunar), GedcomDates.phrase(lunar));

        assertThat(back).isNotNull();
        assertThat(back.calendar()).isEqualTo(CalendarType.LUNAR);
        assertThat(back.year()).isEqualTo(1950);
        assertThat(back.month()).isEqualTo(3);
        assertThat(back.day()).isEqualTo(10);
    }

    @Test
    @DisplayName("a foreign file's date is read as solar, because it carries no lunar information")
    void readsForeignDateAsSolar() {
        GenealogyDateRequest parsed = GedcomDates.parse("ABT 1890", null);

        assertThat(parsed).isNotNull();
        assertThat(parsed.calendar()).isEqualTo(CalendarType.SOLAR);
        assertThat(parsed.modifier()).isEqualTo(DateModifier.ABOUT);
        assertThat(parsed.year()).isEqualTo(1890);
        assertThat(parsed.month()).isNull();
    }

    @Test
    @DisplayName("a full GEDCOM date parses into its parts")
    void parsesFullDate() {
        GenealogyDateRequest parsed = GedcomDates.parse("15 MAR 1890", null);

        assertThat(parsed).isNotNull();
        assertThat(parsed.year()).isEqualTo(1890);
        assertThat(parsed.month()).isEqualTo(3);
        assertThat(parsed.day()).isEqualTo(15);
    }

    @Test
    @DisplayName("a range parses into both endpoints")
    void parsesRange() {
        GenealogyDateRequest parsed = GedcomDates.parse("BET 1918 AND 1922", null);

        assertThat(parsed).isNotNull();
        assertThat(parsed.modifier()).isEqualTo(DateModifier.BETWEEN);
        assertThat(parsed.year()).isEqualTo(1918);
        assertThat(parsed.year2()).isEqualTo(1922);
    }

    @Test
    @DisplayName("a 5.5.1 calendar escape is stripped rather than read as part of the date")
    void stripsCalendarEscape() {
        GenealogyDateRequest parsed = GedcomDates.parse("@#DJULIAN@ 15 MAR 1890", null);

        assertThat(parsed).isNotNull();
        assertThat(parsed.year()).isEqualTo(1890);
        assertThat(parsed.month()).isEqualTo(3);
        assertThat(parsed.day()).isEqualTo(15);
    }

    @Test
    @DisplayName("an impossible day is kept as recorded, because only sort_date is clamped")
    void keepsImpossibleDayAsRecorded() {
        GenealogyDateRequest parsed = GedcomDates.parse("31 FEB 1890", null);

        assertThat(parsed).isNotNull();
        assertThat(parsed.month()).isEqualTo(2);
        // Clamping here rewrote the record: the same date typed into the UI persists as 31 (CLAUDE.md §3.2).
        assertThat(parsed.day()).isEqualTo(31);
    }

    @Test
    @DisplayName("a day that names no day of any month is dropped rather than crashing the import")
    void dropsDayZero() {
        GenealogyDateRequest parsed = GedcomDates.parse("00 MAR 1901", null);

        assertThat(parsed).isNotNull();
        assertThat(parsed.month()).isEqualTo(3);
        assertThat(parsed.day()).isNull();
    }

    @Test
    @DisplayName("an unreadable date yields nothing rather than a wrong year")
    void rejectsUnreadableDate() {
        assertThat(GedcomDates.parse("khong ro", null)).isNull();
        assertThat(GedcomDates.parse("", null)).isNull();
        assertThat(GedcomDates.parse(null, null)).isNull();
    }

    @Test
    @DisplayName("what the family typed is kept in the phrase when GEDCOM cannot say it")
    void keepsRawText() {
        GenealogyDateResponse typed = new GenealogyDateResponse(
                DateModifier.ABOUT, CalendarType.SOLAR, 1890, null, null, null, null, null, "khoảng 1890", null,
                false, false);

        assertThat(GedcomDates.phrase(typed)).isEqualTo("khoảng 1890");
        assertThat(GedcomDates.parse(GedcomDates.format(typed), GedcomDates.phrase(typed)).raw())
                .isEqualTo("khoảng 1890");
    }

    /**
     * Returns the GEDCOM month name of a date.
     *
     * @param date the date
     * @return the three-letter month name
     */
    private static String monthOf(LocalDate date) {
        return new String[] {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"}
                [date.getMonthValue() - 1];
    }
}
