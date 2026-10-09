package com.genealogy.common.model;

/** Access level of an app account. */
public enum Role {

    // Trưởng tộc: full control, including deletes, merges and living-person data.
    ADMIN,

    // Biên tập chi: may add and edit records, but not delete or merge.
    EDITOR,

    // Con cháu: read-only, with living-person details redacted.
    MEMBER;

    /**
     * Reports whether this role may read a living person's details (CLAUDE.md §3.6).
     *
     * @param role the calling member's access level, or null for an unauthenticated caller
     * @return true for EDITOR and ADMIN
     */
    public static boolean maySeeLivingDetails(Role role) {
        // One definition for every service that returns person data, so none of them quietly disagrees.
        return role == ADMIN || role == EDITOR;
    }

    /**
     * Reports whether this role may do what an editor does: review suggestions, read sources and the trail.
     *
     * @param role the calling member's access level, or null for an unauthenticated caller
     * @return true for EDITOR and ADMIN
     */
    // Apart from maySeeLivingDetails although it answers alike today: scoping an EDITOR to a chi would move only one.
    public static boolean isEditorOrAbove(Role role) {
        return role == ADMIN || role == EDITOR;
    }
}
