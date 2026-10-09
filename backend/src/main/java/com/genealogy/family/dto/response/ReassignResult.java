package com.genealogy.family.dto.response;

import java.util.List;
import java.util.Map;

/**
 * What moving one person's unions and parentage onto another actually did.
 *
 * @param unionsMoved unions whose partner slot was repointed
 * @param unionsCollapsed unions that became identical after repointing and were folded together
 * @param childLinksMoved parentage links whose child was repointed
 * @param unionsFoldedInto each folded union's id mapped to the union that survived it
 * @param unionsDropped unions deleted outright, with no survivor to carry their rows
 * @param notes what was dropped rather than moved, one line each
 */
public record ReassignResult(
        int unionsMoved,
        int unionsCollapsed,
        int childLinksMoved,
        Map<Long, Long> unionsFoldedInto,
        List<Long> unionsDropped,
        List<String> notes) {
}
