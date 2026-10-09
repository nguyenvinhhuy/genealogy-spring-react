package com.genealogy.branch.dto.request;

import com.genealogy.common.util.ChangeNotes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating or updating a clan branch.
 *
 * @param name branch name, e.g. "Chi Hai"
 * @param parentId parent branch id, or null for a root branch
 * @param description free-text notes about the chi as a whole, shown to every member
 * @param changeNote why this edit was made — the audit trail's "on what basis" (§3.8)
 * @param version the version the edit was made from, or null to skip the stale-edit check
 */
public record BranchRequest(
        @NotBlank @Size(max = MAX_NAME) String name,
        Long parentId,
        @Size(max = 2000) String description,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
        Long version) {

    // Longest name the column holds, named so a GEDCOM _BRANCH path can be trimmed to it before saving (§8.8 #4).
    public static final int MAX_NAME = 100;
}
