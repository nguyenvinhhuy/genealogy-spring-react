package com.genealogy.common.util;

/** Turns what a family typed into a literal fragment for a SQL LIKE with an {@code ESCAPE '\'} clause. */
public final class LikePattern {

    /** Not instantiable. */
    private LikePattern() {
    }

    /**
     * Escapes the characters LIKE reads as wildcards.
     *
     * @param query what the family typed
     * @return the same text as a literal LIKE fragment, for a query carrying {@code ESCAPE '\'}
     */
    private static String escape(String query) {
        // Backslash first, or the later escapes are escaped twice; private, so no caller skips orNull's blank rule.
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Normalises a search box's text: blank means no filter, anything else is trimmed and escaped.
     *
     * @param query what the family typed, possibly null
     * @return the escaped fragment, or null when there is nothing to search for
     */
    public static String orNull(String query) {
        return query == null || query.isBlank() ? null : escape(query.strip());
    }
}
