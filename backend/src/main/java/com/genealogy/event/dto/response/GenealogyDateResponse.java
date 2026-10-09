package com.genealogy.event.dto.response;

import com.genealogy.common.model.CalendarType;
import com.genealogy.common.model.DateModifier;

/**
 * A genealogical date as returned to clients; {@code sortDate} is absent because it overstates precision (§3.2).
 *
 * @param modifier how precisely the date is known
 * @param calendar SOLAR or LUNAR
 * @param year year, or null
 * @param month month, or null
 * @param day day, or null
 * @param year2 second endpoint's year, only for BETWEEN
 * @param month2 second endpoint's month
 * @param day2 second endpoint's day
 * @param raw what the user originally typed
 * @param display the date written out in Vietnamese, for every surface to print as-is
 * @param leapMonth whether a lunar month is the leap month (tháng nhuận)
 * @param leapMonth2 whether the second endpoint's lunar month is the leap month
 */
public record GenealogyDateResponse(
        DateModifier modifier,
        CalendarType calendar,
        Integer year,
        Integer month,
        Integer day,
        Integer year2,
        Integer month2,
        Integer day2,
        String raw,
        String display,
        boolean leapMonth,
        boolean leapMonth2) {
}
