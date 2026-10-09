package com.genealogy.common.model;

/** How precisely a genealogical date is known. */
public enum DateModifier {

    // The date is known exactly, to whatever precision its parts carry.
    EXACT,

    // Roughly this date ("khoảng 1890").
    ABOUT,

    // Some unknown date before this one ("trước 1900").
    BEFORE,

    // Some unknown date after this one ("sau 1900").
    AFTER,

    // Somewhere in the closed range between the two endpoints ("1918-1922").
    BETWEEN,

    // Guessed from surrounding evidence rather than recorded.
    ESTIMATED,

    // Worked out arithmetically, such as a birth year derived from an age at death.
    CALCULATED
}
