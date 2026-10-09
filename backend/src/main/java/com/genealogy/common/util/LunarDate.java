package com.genealogy.common.util;

/**
 * A date on the Vietnamese lunar calendar.
 *
 * @param day ngày, 1..30
 * @param month tháng, 1..12
 * @param year năm âm lịch
 * @param leapMonth true when this is the intercalary repeat of {@code month} (tháng nhuận)
 */
public record LunarDate(int day, int month, int year, boolean leapMonth) {

    /**
     * Creates a non-leap lunar date.
     *
     * @param day ngày
     * @param month tháng
     * @param year năm
     * @return the date
     */
    public static LunarDate of(int day, int month, int year) {
        return new LunarDate(day, month, year, false);
    }
}
