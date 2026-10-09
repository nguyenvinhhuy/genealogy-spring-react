package com.genealogy.gedcom.domain;

import com.genealogy.common.model.CalendarType;
import com.genealogy.common.model.DateModifier;
import com.genealogy.common.util.Blank;
import com.genealogy.common.util.LunarCalendar;
import com.genealogy.common.util.LunarDate;
import com.genealogy.event.dto.request.GenealogyDateRequest;
import com.genealogy.event.dto.response.GenealogyDateResponse;
import java.util.List;
import java.util.Locale;

/** Converts between a fuzzy {@code GenealogyDate} and a GEDCOM 7 {@code DATE} payload. */
public final class GedcomDates {

    // Marks a lunar date inside an exported DATE PHRASE: GEDCOM 7 has no lunar calendar to write the parts into.
    public static final String LUNAR_PHRASE_PREFIX = "Âm lịch";

    // Follows the lunar marker when the month is the leap month, the one thing the Gregorian day cannot say back.
    private static final String LEAP_MARKER = "nhuận";

    // Separates the machine-written lunar parts from the text the family actually typed.
    private static final String RAW_SEPARATOR = " — ";

    private static final List<String> MONTHS =
            List.of("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC");

    /** Not instantiable. */
    private GedcomDates() {
    }

    /**
     * Renders a date as a GEDCOM 7 DATE payload, converting a lunar date to its Gregorian equivalent.
     *
     * @param date the date to render, or null
     * @return the payload, or null when not even a year is known
     */
    public static String format(GenealogyDateResponse date) {
        if (date == null || date.year() == null) {
            return null;
        }
        // Lunar parts are converted first, then wear the same modifier keyword: a giỗ "khoảng" stays "khoảng".
        boolean lunar = date.calendar() == CalendarType.LUNAR;
        String from = lunar
                ? solarEquivalent(date.year(), date.month(), date.day(), date.leapMonth())
                : part(date.year(), date.month(), date.day());
        String to = lunar
                ? solarEquivalent(endYear(date), date.month2(), date.day2(), date.leapMonth2())
                : part(endYear(date), date.month2(), date.day2());

        return switch (date.modifier()) {
            case EXACT -> from;
            case ABOUT -> "ABT " + from;
            case ESTIMATED -> "EST " + from;
            case CALCULATED -> "CAL " + from;
            case BEFORE -> "BEF " + from;
            case AFTER -> "AFT " + from;
            case BETWEEN -> "BET " + from + " AND " + to;
        };
    }

    /**
     * Renders the note that belongs beside an exported date, preserving what a GEDCOM DATE cannot hold.
     *
     * @param date the date being exported, or null
     * @return the PHRASE payload, or null when the DATE says everything
     */
    public static String phrase(GenealogyDateResponse date) {
        if (date == null) {
            return null;
        }
        boolean lunarDayMonth = date.calendar() == CalendarType.LUNAR && date.month() != null;
        // A date with no readable year has nothing but the family's words; GEDCOM 7 carries those in a PHRASE.
        if (date.year() == null && !lunarDayMonth) {
            return Blank.toNull(date.raw());
        }
        // Keep what the family typed, so "khoảng 1890" is not flattened to "ABT 1890" forever.
        String raw = date.raw() == null ? "" : date.raw().strip();
        if (date.calendar() != CalendarType.LUNAR) {
            return raw.isBlank() || raw.equalsIgnoreCase(format(date)) ? null : raw;
        }

        // A giỗ with no year (§8.10 D1) still travels: the DATE is empty and the phrase holds day and month.
        StringBuilder lunar = new StringBuilder(LUNAR_PHRASE_PREFIX)
                .append(date.leapMonth() ? " " + LEAP_MARKER + " " : " ")
                .append(date.year() == null
                        ? dayMonth(date.month(), date.day())
                        : part(date.year(), date.month(), date.day()));
        if (date.modifier() == DateModifier.BETWEEN && date.year2() != null) {
            lunar.append(" AND ")
                    .append(date.leapMonth2() ? LEAP_MARKER + " " : "")
                    .append(part(date.year2(), date.month2(), date.day2()));
        }
        // Appended rather than replaced: the lunar marker is ours, the raw text is the family's own words.
        return raw.isBlank() ? lunar.toString() : lunar.append(RAW_SEPARATOR).append(raw).toString();
    }

    /**
     * Reads a GEDCOM DATE payload back into date parts.
     *
     * @param value the DATE payload, or null
     * @param phrase the DATE PHRASE payload, or null
     * @return the parsed date, or null when no year could be read
     */
    public static GenealogyDateRequest parse(String value, String phrase) {
        String text = value == null ? "" : stripCalendarEscape(value.strip()).toUpperCase(Locale.ROOT);
        // Read before the empty-DATE branch: our export writes a year-less giỗ as an empty DATE and a phrase.
        GenealogyDateRequest lunar = lunarFromPhrase(phrase, text);
        if (lunar != null) {
            return lunar;
        }
        if (value == null || value.isBlank()) {
            // GEDCOM 7 allows an empty DATE beside a PHRASE, and §3.2 keeps that text rather than dropping it.
            return phrase == null || phrase.isBlank()
                    ? null
                    : new GenealogyDateRequest(
                            DateModifier.EXACT, CalendarType.SOLAR, null, null, null,
                            null, null, null, phrase.strip(), false, false);
        }
        String raw = phrase != null && !phrase.isBlank() ? phrase : value.strip();

        GenealogyDateRequest range = rangeOf(text, raw);
        if (range != null) {
            return range;
        }
        // A BET payload is always a range; unreadable as one, reading it as EXACT invents a date the file never gave.
        if (text.startsWith("BET ")) {
            return null;
        }

        DateModifier modifier = modifierOf(text);
        Parts parts = Parts.parse(stripModifier(text));
        return parts == null
                ? null
                : new GenealogyDateRequest(
                        modifier, CalendarType.SOLAR, parts.year(), parts.month(), parts.day(),
                        null, null, null, raw, false, false);
    }

    /**
     * Reads a two-endpoint GEDCOM range such as {@code BET 1918 AND 1922} or {@code FROM 1918 TO 1922}.
     *
     * @param text the upper-cased payload
     * @param raw the text to keep verbatim
     * @return the range, or null when the payload names no range
     */
    private static GenealogyDateRequest rangeOf(String text, String raw) {
        String separator = text.startsWith("BET ") ? " AND " : text.startsWith("FROM ") ? " TO " : null;
        if (separator == null) {
            return null;
        }
        int prefix = text.startsWith("BET ") ? 4 : 5;
        int split = text.indexOf(separator);
        // Guarded: `BET AND 1900` puts the separator inside the keyword, and substring would throw.
        if (split < prefix) {
            return null;
        }

        Parts from = Parts.parse(text.substring(prefix, split));
        Parts to = Parts.parse(text.substring(split + separator.length()));
        if (from == null) {
            return null;
        }
        if (to == null) {
            // A range missing its upper bound is only evidence of the lower one; BETWEEN would derive a null midpoint.
            return new GenealogyDateRequest(
                    DateModifier.AFTER, CalendarType.SOLAR, from.year(), from.month(), from.day(),
                    null, null, null, raw, false, false);
        }
        return new GenealogyDateRequest(
                DateModifier.BETWEEN, CalendarType.SOLAR, from.year(), from.month(), from.day(),
                to.year(), to.month(), to.day(), raw, false, false);
    }

    /**
     * Rebuilds the original lunar date from the phrase our own export wrote.
     *
     * @param phrase the PHRASE payload, or null
     * @param text the upper-cased DATE payload, read for its modifier keyword
     * @return the lunar date, or null when this phrase is not one of ours
     */
    private static GenealogyDateRequest lunarFromPhrase(String phrase, String text) {
        if (phrase == null || !phrase.strip().startsWith(LUNAR_PHRASE_PREFIX)) {
            return null;
        }
        String body = phrase.strip().substring(LUNAR_PHRASE_PREFIX.length()).strip();
        int rawAt = body.indexOf(RAW_SEPARATOR);
        String raw = rawAt < 0 ? "" : body.substring(rawAt + RAW_SEPARATOR.length()).strip();
        String dates = rawAt < 0 ? body : body.substring(0, rawAt);

        // Split before upper-casing: each endpoint carries its own "nhuận", and that marker is lower case.
        int split = dates.indexOf(" AND ");
        String fromText = split < 0 ? dates : dates.substring(0, split);
        String toText = split < 0 ? null : dates.substring(split + 5);
        boolean leap = fromText.strip().startsWith(LEAP_MARKER);
        boolean leap2 = toText != null && toText.strip().startsWith(LEAP_MARKER);
        Parts from = Parts.parse(withoutLeap(fromText), true);
        Parts to = toText == null ? null : Parts.parse(withoutLeap(toText), false);
        // Falls back to solar rather than discarding the date: a family may simply have typed these words.
        if (from == null) {
            return null;
        }
        DateModifier modifier = to == null ? modifierOf(text) : DateModifier.BETWEEN;
        return new GenealogyDateRequest(
                modifier, CalendarType.LUNAR, from.year(), from.month(), from.day(),
                to == null ? null : to.year(), to == null ? null : to.month(),
                to == null ? null : to.day(), raw.isBlank() ? phrase.strip() : raw,
                leap && from.month() != null, leap2 && to != null && to.month() != null);
    }

    /**
     * Removes a leading leap marker from one endpoint of a lunar phrase and upper-cases the rest.
     *
     * @param text the endpoint as written
     * @return the endpoint, ready for {@link Parts#parse}
     */
    private static String withoutLeap(String text) {
        String stripped = text.strip();
        return (stripped.startsWith(LEAP_MARKER) ? stripped.substring(LEAP_MARKER.length()) : stripped)
                .strip()
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Renders a lunar day and month with no year, the way the rest of the phrase spells months.
     *
     * @param month the month, never null
     * @param day the day, or null
     * @return the rendered day and month
     */
    private static String dayMonth(Integer month, Integer day) {
        String monthName = MONTHS.get(month - 1);
        return day == null ? monthName : day + " " + monthName;
    }

    /**
     * Renders a lunar date as the Gregorian day it actually fell on.
     *
     * @param year the lunar year, or null
     * @param month the lunar month, or null
     * @param day the lunar day, or null
     * @param leap whether the month is the leap month
     * @return the GEDCOM payload, or null when not even a year is known
     */
    private static String solarEquivalent(Integer year, Integer month, Integer day, boolean leap) {
        if (year == null) {
            return null;
        }
        if (month == null || day == null) {
            // Without a full lunar date there is nothing to convert; the year is the same either way.
            return String.valueOf(year);
        }
        return LunarCalendar.toSolar(new LunarDate(day, month, year, leap))
                .map(solar -> part(solar.getYear(), solar.getMonthValue(), solar.getDayOfMonth()))
                .orElseGet(() -> String.valueOf(year));
    }

    /**
     * Renders year, month and day in the order and spelling GEDCOM uses.
     *
     * @param year the year, never null
     * @param month the month, or null
     * @param day the day, or null
     * @return the rendered date
     */
    public static String part(Integer year, Integer month, Integer day) {
        if (month == null || month < 1 || month > 12) {
            return String.valueOf(year);
        }
        String monthName = MONTHS.get(month - 1);
        return day == null ? monthName + " " + year : day + " " + monthName + " " + year;
    }

    /**
     * Picks the second endpoint's year, falling back to the first when a range names only one.
     *
     * @param date the date being rendered
     * @return the year to render as the upper bound
     */
    private static Integer endYear(GenealogyDateResponse date) {
        return date.year2() != null ? date.year2() : date.year();
    }

    /**
     * Maps a GEDCOM date prefix to the modifier it means.
     *
     * @param text the upper-cased payload
     * @return the modifier, EXACT when the payload carries no prefix
     */
    private static DateModifier modifierOf(String text) {
        if (text.startsWith("ABT ")) {
            return DateModifier.ABOUT;
        }
        if (text.startsWith("EST ")) {
            return DateModifier.ESTIMATED;
        }
        if (text.startsWith("CAL ")) {
            return DateModifier.CALCULATED;
        }
        if (text.startsWith("BEF ")) {
            return DateModifier.BEFORE;
        }
        if (text.startsWith("AFT ") || text.startsWith("FROM ")) {
            return DateModifier.AFTER;
        }
        // `TO 1922` with no FROM names an upper bound; read as EXACT it would assert the event happened then.
        if (text.startsWith("TO ")) {
            return DateModifier.BEFORE;
        }
        return DateModifier.EXACT;
    }

    /**
     * Removes the modifier prefix from a date payload.
     *
     * @param text the upper-cased payload
     * @return the payload with any leading keyword removed
     */
    private static String stripModifier(String text) {
        for (String prefix : List.of("ABT ", "EST ", "CAL ", "BEF ", "AFT ", "FROM ", "TO ", "INT ")) {
            if (text.startsWith(prefix)) {
                return text.substring(prefix.length()).strip();
            }
        }
        return text;
    }

    /**
     * Removes a GEDCOM 5.5.1 calendar escape such as {@code @#DJULIAN@}.
     *
     * @param text the payload
     * @return the payload with any leading escape removed
     */
    private static String stripCalendarEscape(String text) {
        if (!text.startsWith("@#")) {
            return text;
        }
        int close = text.indexOf('@', 2);
        return close < 0 ? text : text.substring(close + 1).stripLeading();
    }

    /**
     * A GEDCOM date payload split into its numeric parts.
     *
     * @param year the year, or null for a lunar day and month with no year
     * @param month the month, or null
     * @param day the day, or null
     */
    private record Parts(Integer year, Integer month, Integer day) {

        /**
         * Reads a bare GEDCOM date such as {@code 1 JAN 1890}, {@code JAN 1890} or {@code 1890}, which needs a year.
         *
         * @param text the payload with any modifier already removed
         * @return the parts, or null when no year could be read
         */
        static Parts parse(String text) {
            return parse(text, false);
        }

        /**
         * Reads a bare GEDCOM date, optionally accepting a day and month with no year.
         *
         * @param text the payload with any modifier already removed
         * @param yearOptional whether a month with no year is a date, as a lunar giỗ is (§8.10 D1)
         * @return the parts, or null when nothing placeable could be read
         */
        static Parts parse(String text, boolean yearOptional) {
            String[] tokens = text.strip().split("\\s+");
            Integer year = null;
            Integer month = null;
            Integer day = null;
            for (String token : tokens) {
                int monthIndex = MONTHS.indexOf(token);
                if (monthIndex >= 0) {
                    month = monthIndex + 1;
                } else if (token.matches("\\d{1,2}") && day == null && year == null) {
                    day = Integer.parseInt(token);
                } else if (token.matches("\\d{3,4}") && year == null) {
                    // First year wins: a payload naming two makes the second an endpoint, which rangeOf owns.
                    year = Integer.parseInt(token);
                }
            }
            if (year == null && !(yearOptional && month != null)) {
                return null;
            }
            // A day without a month says nothing GEDCOM can place, and LocalDate would reject it later.
            return new Parts(year, month, month == null ? null : readableDay(day));
        }

        /**
         * Keeps a day only when it could name a real day of some month.
         *
         * @param day the day as recorded, or null
         * @return the day unchanged, or null when it names none
         */
        private static Integer readableDay(Integer day) {
            // Not clamped to the month's length: §3.2 keeps 31 February as recorded and clamps only sort_date.
            return day == null || day < 1 || day > 31 ? null : day;
        }
    }
}
