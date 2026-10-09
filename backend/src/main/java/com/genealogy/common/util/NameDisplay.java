package com.genealogy.common.util;

/** Renders a Vietnamese name surname-first, the way it is always written (CLAUDE.md §5.2). */
public final class NameDisplay {

    /** Not instantiable. */
    private NameDisplay() {
    }

    /**
     * Joins the parts of a name, surname first, skipping any that were not recorded.
     *
     * @param surname the họ, or null
     * @param middleName the tên đệm, or null
     * @param givenName the tên
     * @return the display name
     */
    public static String of(String surname, String middleName, String givenName) {
        StringBuilder builder = new StringBuilder();
        if (surname != null && !surname.isBlank()) {
            builder.append(surname.strip()).append(' ');
        }
        if (middleName != null && !middleName.isBlank()) {
            builder.append(middleName.strip()).append(' ');
        }
        return builder.append(givenName == null ? "" : givenName.strip()).toString().strip();
    }
}
