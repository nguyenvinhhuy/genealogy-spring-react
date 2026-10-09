package com.genealogy.person.dto.response;

import com.genealogy.common.model.Gender;

/**
 * A living person as shown to a caller below EDITOR: enough to place them in the tree, nothing more (§3.6).
 *
 * @param id person id
 * @param displayName the primary name, rendered surname-first
 * @param gender recorded sex, kept because "chồng/vợ" is derived from it at the DTO layer (§3.1)
 * @param generation đời, kept because the tree is unreadable without it
 * @param living always true — a redacted view only ever describes a living person
 * @param redacted always true, so a client can say "ẩn" instead of rendering blanks as facts
 */
public record PersonRedactedResponse(
        Long id,
        String displayName,
        Gender gender,
        Integer generation,
        boolean living,
        boolean redacted)
        implements PersonView {

    /**
     * Builds the redacted view of a person.
     *
     * @param id person id
     * @param displayName the primary name
     * @param gender recorded sex
     * @param generation đời, or null
     * @return the redacted view
     */
    public static PersonRedactedResponse of(Long id, String displayName, Gender gender, Integer generation) {
        return new PersonRedactedResponse(id, displayName, gender, generation, true, true);
    }
}
