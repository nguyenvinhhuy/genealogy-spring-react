package com.genealogy.event.dto.response;

import java.time.LocalDate;

/**
 * One upcoming ngày giỗ, carrying both calendars: the family knows the lunar date but diarises the solar one.
 *
 * @param eventId the death event it is computed from, unique even when one person has two recorded deaths
 * @param personId whose anniversary it is
 * @param personName their primary name
 * @param lunarDay ngày âm of the anniversary
 * @param lunarMonth tháng âm of the anniversary
 * @param leapMonth whether the death fell in a leap month, kept in the ordinary month of that number
 * @param approximate whether the recorded death day was only an estimate
 * @param nextOccurrence the Gregorian date it next falls on
 * @param daysUntil how many days away that is
 * @param yearsSince how many lunar years since the death, or null when the death year is unknown
 */
public record AnniversaryResponse(
        Long eventId,
        Long personId,
        String personName,
        int lunarDay,
        int lunarMonth,
        boolean leapMonth,
        boolean approximate,
        LocalDate nextOccurrence,
        long daysUntil,
        Integer yearsSince) {
}
