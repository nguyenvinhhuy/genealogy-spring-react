package com.genealogy.common.util;

import com.genealogy.common.model.PersonNameType;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** The one rule for when two recorded names are the same name, used by merge and by suggestion approval alike. */
// Two rules had drifted: suggestions matched exactly, merges compared the lowercased display string (§8.6).
public final class NameKey {

    private static final Pattern RUNS_OF_SPACE = Pattern.compile("\\s+");

    /** Not instantiable. */
    private NameKey() {
    }

    /**
     * Builds the comparison key of one name.
     *
     * @param type what kind of name it is
     * @param surname the họ, or null
     * @param middleName the tên đệm, or null
     * @param givenName the tên
     * @return a key equal for two names that record the same thing
     */
    public static String of(PersonNameType type, String surname, String middleName, String givenName) {
        // Case and spacing are how it was typed; diacritics are not: "Ánh" and "Anh" are two different names.
        return type + "|" + part(surname) + "|" + part(middleName) + "|" + part(givenName);
    }

    /**
     * Normalises one name part for comparison.
     *
     * @param value the part, or null
     * @return the part composed, trimmed, with inner runs of space collapsed and lowercased; empty for null
     */
    private static String part(String value) {
        if (value == null) {
            return "";
        }
        // Composed first: "Ánh" typed on an iPhone arrives decomposed, and is the same name as on Windows (§8.10 #7).
        String composed = Normalizer.normalize(value, Normalizer.Form.NFC);
        return RUNS_OF_SPACE.matcher(composed.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
