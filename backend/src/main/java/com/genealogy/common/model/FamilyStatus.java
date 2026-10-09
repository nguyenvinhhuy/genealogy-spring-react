package com.genealogy.common.model;

/** State of the union between two partners. */
public enum FamilyStatus {

    // Kết hôn.
    MARRIED,

    // Đã ly hôn.
    DIVORCED,

    // Sống chung without a recorded marriage.
    PARTNER,

    // The record says a couple existed but not what kind.
    UNKNOWN
}
