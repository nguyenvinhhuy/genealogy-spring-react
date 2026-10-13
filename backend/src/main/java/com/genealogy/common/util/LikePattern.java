package com.genealogy.common.util;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Splits a search box's text into escaped words, padded with nulls to a fixed count.
     *
     * @param query what the family typed, possibly null
     * @param count how many words the query can carry; words past it are dropped
     * @return exactly {@code count} entries: the escaped words, then nulls
     */
    public static List<String> words(String query, int count) {
        List<String> words = new ArrayList<>(count);
        if (query != null) {
            for (String word : query.strip().split("\\s+")) {
                if (!word.isEmpty() && words.size() < count) {
                    words.add(escape(word));
                }
            }
        }
        while (words.size() < count) {
            words.add(null);
        }
        return words;
    }
}
