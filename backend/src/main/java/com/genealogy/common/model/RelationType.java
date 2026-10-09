package com.genealogy.common.model;

/** How a child is related to one partner of the family they were linked into. */
public enum RelationType {

    // Con đẻ: biological child of that partner.
    BIRTH,

    // Con nuôi: legally or socially adopted.
    ADOPTED,

    // Con riêng: the other partner's child from a previous union.
    STEP,

    // Con nuôi dưỡng: raised by, but never formally adopted.
    FOSTER
}
