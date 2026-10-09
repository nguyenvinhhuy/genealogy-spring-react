package com.genealogy.event.dto.request;

import com.genealogy.common.model.CalendarType;
import com.genealogy.common.model.DateModifier;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * A genealogical date as submitted by a client; only {@code year} is really required (CLAUDE.md §3.2).
 *
 * @param modifier how precisely the date is known, defaults to EXACT
 * @param calendar SOLAR or LUNAR, defaults to SOLAR
 * @param year year, or null when nothing at all is known
 * @param month month, only meaningful with a year
 * @param day day, only meaningful with a month
 * @param year2 second endpoint's year, required for and only for BETWEEN
 * @param month2 second endpoint's month
 * @param day2 second endpoint's day
 * @param raw what the user actually typed, kept verbatim
 * @param leapMonth whether a lunar month is the leap month (tháng nhuận), null meaning no
 * @param leapMonth2 whether the second endpoint's lunar month is the leap month, null meaning no
 */
public record GenealogyDateRequest(
        DateModifier modifier,
        CalendarType calendar,
        @Min(MIN_YEAR) @Max(MAX_YEAR) Integer year,
        @Min(1) @Max(12) Integer month,
        @Min(1) @Max(31) Integer day,
        @Min(MIN_YEAR) @Max(MAX_YEAR) Integer year2,
        @Min(1) @Max(12) Integer month2,
        @Min(1) @Max(31) Integer day2,
        String raw,
        Boolean leapMonth,
        Boolean leapMonth2) {

    // Wide enough for any gia phả and narrow enough that every derived day is a date Postgres can store.
    public static final int MIN_YEAR = 1;
    public static final int MAX_YEAR = 9999;
}
