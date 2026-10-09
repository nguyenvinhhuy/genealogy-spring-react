package com.genealogy.tree.domain;

/** Which way a tree request expands from its focus person. */
public enum TreeDirection {

    // Con cháu: expand downward only.
    DESCENDANTS,

    // Tổ tiên: expand upward only.
    ANCESTORS,

    // Đồng hồ cát: expand both ways around the focus person.
    HOURGLASS
}
