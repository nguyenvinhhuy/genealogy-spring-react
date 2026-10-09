package com.genealogy.tree.dto.response;

import com.genealogy.family.dto.response.UnionEdgeResponse;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.tree.domain.TreeDirection;
import java.util.List;
import java.util.Set;

/**
 * The bounded BFS slice of the gia phả around one focus person (CLAUDE.md §3.5).
 *
 * @param focusId the person the walk started from
 * @param direction which way it expanded
 * @param depth how many generations were walked, after clamping
 * @param truncatedBelow true when descendants exist beyond the returned depth
 * @param truncatedAbove true when ancestors exist beyond the returned depth
 * @param persons every person in the slice
 * @param unions every union in the slice, with its children
 * @param recordedDeadIds the people in the slice with a recorded death or burial
 */
public record SubgraphResponse(
        Long focusId,
        TreeDirection direction,
        int depth,
        boolean truncatedBelow,
        boolean truncatedAbove,
        List<PersonNodeResponse> persons,
        List<UnionEdgeResponse> unions,
        Set<Long> recordedDeadIds) {
}
