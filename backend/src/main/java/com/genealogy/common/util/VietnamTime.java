package com.genealogy.common.util;

import java.time.LocalDate;
import java.time.ZoneId;

/** The clan's local calendar day, which is what a ngày giỗ and a birthday are counted in. */
public final class VietnamTime {

    // A String as well as a ZoneId, because an annotation such as @Scheduled(zone = ...) needs a constant.
    public static final String ZONE_ID = "Asia/Ho_Chi_Minh";
    // The container runs in UTC, so a bare LocalDate.now() still says yesterday until 07:00 in Hà Nội.
    private static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    /** Not instantiable. */
    private VietnamTime() {
    }

    /**
     * Returns today's date in Vietnam.
     *
     * @return the local date at UTC+7
     */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
