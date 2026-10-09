package com.genealogy.common.util;

/** Cuts text to the length a column allows without splitting a character in two. */
public final class TextCut {

    /** Not instantiable. */
    private TextCut() {
    }

    /**
     * Cuts a string to a maximum length.
     *
     * @param text the text, or null
     * @param max the longest the column allows
     * @return the text, shortened when it was too long
     */
    public static String toLength(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        if (max <= 0) {
            return "";
        }
        int end = max;
        // Backed off a char when the cut lands inside a surrogate pair: half a chữ Hán is not encodable as UTF-8.
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        // Backed off over tone marks too: a decomposed "ễ" cut after its "e" silently loses the mark (§8.10 #7).
        while (end > 0 && Character.getType(text.charAt(end)) == Character.NON_SPACING_MARK) {
            end--;
        }
        return text.substring(0, end);
    }
}
