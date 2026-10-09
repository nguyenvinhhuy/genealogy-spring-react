package com.genealogy.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for the one Vietnamese rendering of a fuzzy date (CLAUDE.md §3.2). */
class GenealogyDateTextTest {

    /**
     * Builds a date with the given parts.
     *
     * @param modifier the precision modifier
     * @param calendar the calendar
     * @param y year, may be null
     * @param m month, may be null
     * @param d day, may be null
     * @return the date
     */
    private static GenealogyDate date(DateModifier modifier, CalendarType calendar, Integer y, Integer m, Integer d) {
        GenealogyDate date = new GenealogyDate();
        date.setModifier(modifier);
        date.setCalendar(calendar);
        date.setYear(y);
        date.setMonth(m);
        date.setDay(d);
        return date;
    }

    /**
     * Renders a solar date with the given parts.
     *
     * @param modifier the precision modifier
     * @param y year, may be null
     * @param m month, may be null
     * @param d day, may be null
     * @return the rendered text
     */
    private static String solar(DateModifier modifier, Integer y, Integer m, Integer d) {
        return GenealogyDateText.of(date(modifier, CalendarType.SOLAR, y, m, d));
    }

    @Test
    @DisplayName("a date prints exactly as much precision as was recorded, never more")
    void printsOnlyRecordedPrecision() {
        assertThat(solar(DateModifier.EXACT, 1890, 3, 15)).isEqualTo("15/3/1890");
        assertThat(solar(DateModifier.EXACT, 1890, 3, null)).isEqualTo("3/1890");
        assertThat(solar(DateModifier.EXACT, 1890, null, null)).isEqualTo("1890");
    }

    @ParameterizedTest(name = "{0} prints as \"{1}\"")
    @DisplayName("each modifier prints the Vietnamese word for it")
    @CsvSource({
        "ABOUT, khoảng 1890",
        "BEFORE, trước 1890",
        "AFTER, sau 1890",
        "ESTIMATED, ước tính 1890",
        "CALCULATED, tính ra 1890",
    })
    void printsModifiers(DateModifier modifier, String text) {
        assertThat(solar(modifier, 1890, null, null)).isEqualTo(text);
    }

    @Test
    @DisplayName("a range prints both endpoints as a range, not as an estimate")
    void printsRange() {
        GenealogyDate range = date(DateModifier.BETWEEN, CalendarType.SOLAR, 1918, 3, null);
        range.setYear2(1922);
        range.setMonth2(6);

        // "khoảng 1918–1922" read like ABOUT; a range has two firm ends.
        assertThat(GenealogyDateText.of(range)).isEqualTo("từ 3/1918 đến 6/1922");
    }

    @Test
    @DisplayName("a lunar date says so and names its year by can-chi, because the same numbers mean a different day")
    void marksLunarDates() {
        assertThat(GenealogyDateText.of(date(DateModifier.EXACT, CalendarType.LUNAR, 1950, 3, 10)))
                .isEqualTo("10/3/1950 (âm lịch, năm Canh Dần)");
        assertThat(GenealogyDateText.of(date(DateModifier.ABOUT, CalendarType.LUNAR, 1890, 3, null)))
                .isEqualTo("khoảng 3/1890 (âm lịch, năm Canh Dần)");
    }

    @Test
    @DisplayName("a tháng nhuận is written beside its own month")
    void marksTheLeapMonth() {
        GenealogyDate leap = date(DateModifier.EXACT, CalendarType.LUNAR, 2020, 4, 10);
        leap.setLeapMonth(true);

        assertThat(GenealogyDateText.of(leap)).isEqualTo("10/4 nhuận/2020 (âm lịch, năm Canh Tý)");
    }

    @Test
    @DisplayName("in a lunar range, only the endpoint that is in the leap month says so")
    void marksTheLeapEndpointOnly() {
        GenealogyDate range = date(DateModifier.BETWEEN, CalendarType.LUNAR, 2023, 1, 1);
        range.setYear2(2023);
        range.setMonth2(2);
        range.setDay2(15);
        range.setLeapMonth2(true);

        assertThat(GenealogyDateText.of(range)).isEqualTo("từ 1/1/2023 đến 15/2 nhuận/2023 (âm lịch, năm Quý Mão)");
    }

    @Test
    @DisplayName("a lunar range across years names no single can-chi")
    void lunarRangeAcrossYears() {
        GenealogyDate range = date(DateModifier.BETWEEN, CalendarType.LUNAR, 1918, null, null);
        range.setYear2(1922);

        assertThat(GenealogyDateText.of(range)).isEqualTo("từ 1918 đến 1922 (âm lịch)");
    }

    @Test
    @DisplayName("a giỗ known only by lunar day and month says the year is unknown")
    void lunarDayAndMonthWithoutAYear() {
        assertThat(GenealogyDateText.of(date(DateModifier.EXACT, CalendarType.LUNAR, null, 3, 12)))
                .isEqualTo("12/3 (âm lịch, không rõ năm)");
    }

    @Test
    @DisplayName("a date with no year falls back to what the family typed rather than printing nothing")
    void fallsBackToRawText() {
        GenealogyDate typed = date(DateModifier.EXACT, CalendarType.SOLAR, null, null, null);
        typed.setRaw(" đời Tự Đức ");

        assertThat(GenealogyDateText.of(typed)).isEqualTo("đời Tự Đức");
        assertThat(solar(DateModifier.EXACT, null, null, null)).isNull();
        assertThat(GenealogyDateText.of(null)).isNull();
    }
}
