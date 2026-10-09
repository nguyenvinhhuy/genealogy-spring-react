package com.genealogy.common.util;

/** Turns what a form sent into what is stored: surrounding space trimmed, nothing at all stored as null. */
// One rule: "" and null differ to a NULLS NOT DISTINCT unique, and five private copies each had to remember it.
public final class Blank {

    /** Not instantiable. */
    private Blank() {
    }

    /**
     * Trims text, returning null when nothing is left.
     *
     * @param text the text, or null
     * @return the trimmed text, or null when it was missing or blank
     */
    public static String toNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /**
     * Joins two texts with a blank line, keeping one copy when they say the same thing.
     *
     * @param kept the text that stays, or null
     * @param extra the text being added to it, or null
     * @return both, one of them, or null when neither has anything
     */
    public static String join(String kept, String extra) {
        if (extra == null || extra.isBlank()) {
            return kept;
        }
        if (kept == null || kept.isBlank()) {
            return extra;
        }
        // The same note on both people, from a file imported twice, would otherwise be written out twice.
        return kept.strip().equals(extra.strip()) ? kept : kept + "\n\n" + extra;
    }
}
