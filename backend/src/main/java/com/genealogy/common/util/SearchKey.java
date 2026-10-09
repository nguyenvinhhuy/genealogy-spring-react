package com.genealogy.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** The accent-insensitive key of a name: the Java twin of the database's {@code immutable_unaccent(lower(…))}. */
// Unlike NameKey it drops diacritics: duplicate detection must find what search finds (§4.3), "Do Van Duc" included.
public final class SearchKey {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private static final Pattern RUNS_OF_SPACE = Pattern.compile("\\s+");

    /** Not instantiable. */
    private SearchKey() {
    }

    /**
     * Strips accents, case and spacing so two spellings of one name compare equal.
     *
     * @param text the text, or null
     * @return the comparison key, empty for missing text
     */
    public static String of(String text) {
        if (text == null) {
            return "";
        }
        String stripped = COMBINING_MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        // đ has no Unicode decomposition, so NFD keeps it where the database's unaccent maps it to d (§4.3).
        String ascii = stripped.replace('đ', 'd').replace('Đ', 'D');
        return RUNS_OF_SPACE.matcher(ascii).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
