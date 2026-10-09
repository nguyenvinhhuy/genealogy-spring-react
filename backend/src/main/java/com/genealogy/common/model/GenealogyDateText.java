package com.genealogy.common.model;

import com.genealogy.common.util.Blank;
import com.genealogy.common.util.LunarCalendar;

/** Renders a fuzzy date the way a Vietnamese gia phả writes one. */
// One renderer for every surface: the book and the web page used to write the same giỗ two different ways.
public final class GenealogyDateText {

    private static final String LUNAR = "âm lịch";

    // Written beside the month it belongs to, so a range's two endpoints say which of them is the tháng nhuận.
    private static final String LEAP = " nhuận";

    /** Not instantiable. */
    private GenealogyDateText() {
    }

    /**
     * Renders a date in Vietnamese, keeping exactly as much precision as was recorded.
     *
     * @param date the date, or null
     * @return the rendered text, or null when the date says nothing at all
     */
    public static String of(GenealogyDate date) {
        if (date == null) {
            return null;
        }
        boolean lunar = date.getCalendar() == CalendarType.LUNAR;
        if (date.getYear() == null) {
            // A giỗ known only by its day and month is the commonest record of a thuỷ tổ (§8.10 D1).
            if (lunar && date.getMonth() != null) {
                return parts(null, date.getMonth(), date.getDay(), date.isLeapMonth())
                        + " (" + LUNAR + ", không rõ năm)";
            }
            // A date with no parsable year is still a date the family wrote down (§3.2: raw is never discarded).
            return Blank.toNull(date.getRaw());
        }

        String from = parts(date.getYear(), date.getMonth(), date.getDay(), lunar && date.isLeapMonth());
        DateModifier modifier = date.getModifier() == null ? DateModifier.EXACT : date.getModifier();
        String text = switch (modifier) {
            case EXACT -> from;
            case ABOUT -> "khoảng " + from;
            case BEFORE -> "trước " + from;
            case AFTER -> "sau " + from;
            case ESTIMATED -> "ước tính " + from;
            case CALCULATED -> "tính ra " + from;
            // "từ … đến …", not "khoảng": a range has two firm ends, an estimate has none.
            case BETWEEN -> "từ " + from + " đến " + parts(
                    date.getYear2() == null ? date.getYear() : date.getYear2(),
                    date.getMonth2(), date.getDay2(), lunar && date.isLeapMonth2());
        };
        if (!lunar) {
            return text;
        }
        // Never silently drop the calendar: "10/3/1950" read as solar is a different day from the giỗ.
        boolean oneYear = modifier != DateModifier.BETWEEN || date.getYear2() == null
                || date.getYear2().equals(date.getYear());
        return oneYear
                ? text + " (" + LUNAR + ", năm " + LunarCalendar.canChi(date.getYear()) + ")"
                : text + " (" + LUNAR + ")";
    }

    /**
     * Renders year, month and day, omitting whatever was never recorded.
     *
     * @param year the year, or null for a date known only by its day and month
     * @param month the month, or null
     * @param day the day, or null
     * @param leap whether the month is the tháng nhuận
     * @return the rendered parts
     */
    private static String parts(Integer year, Integer month, Integer day, boolean leap) {
        if (month == null) {
            return String.valueOf(year);
        }
        String monthText = month + (leap ? LEAP : "");
        String dayMonth = day == null ? monthText : day + "/" + monthText;
        return year == null ? dayMonth : dayMonth + "/" + year;
    }
}
