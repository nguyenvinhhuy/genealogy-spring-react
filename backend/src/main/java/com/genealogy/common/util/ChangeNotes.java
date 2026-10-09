package com.genealogy.common.util;

/** The limit every "why was this changed" note shares, wherever the audit trail takes one (CLAUDE.md §3.8). */
// One constant: the same audit field was capped on six requests and unbounded on thirteen others (§8.10 #28).
public final class ChangeNotes {

    // Longest reason a revision stores, and the same on a form, a delete parameter and a merge.
    public static final int MAX_LENGTH = 2000;

    /** Not instantiable. */
    private ChangeNotes() {
    }
}
