package com.genealogy.person.dto.request;

import com.genealogy.common.model.PersonNameType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One name in a person payload.
 *
 * @param type which kind of name this is
 * @param surname ho, optional
 * @param middleName ten dem, optional
 * @param givenName ten, required
 * @param primary whether this is the person's main name
 */
public record PersonNameRequest(
        @NotNull PersonNameType type,
        @Size(max = MAX_SURNAME) String surname,
        @Size(max = MAX_MIDDLE_NAME) String middleName,
        @NotBlank @Size(max = MAX_GIVEN_NAME) String givenName,
        boolean primary) {

    // Longest họ the column holds, named so a caller that trims before saving reads the limit (§9).
    public static final int MAX_SURNAME = 50;

    // Longest tên đệm the column holds.
    public static final int MAX_MIDDLE_NAME = 100;

    // Longest tên the column holds.
    public static final int MAX_GIVEN_NAME = 50;

    /**
     * Reports whether the primary name of a list, or its first when none is flagged, carries a họ.
     *
     * @param names the names, may be null
     * @return true when that name has a non-blank surname
     */
    public static boolean primaryHasSurname(List<PersonNameRequest> names) {
        if (names == null || names.isEmpty()) {
            return false;
        }
        PersonNameRequest primary = names.stream().filter(PersonNameRequest::primary).findFirst()
                .orElse(names.getFirst());
        return primary != null && primary.surname() != null && !primary.surname().isBlank();
    }
}
