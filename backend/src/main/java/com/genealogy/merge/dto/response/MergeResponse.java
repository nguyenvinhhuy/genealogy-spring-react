package com.genealogy.merge.dto.response;

import java.util.List;

/**
 * What a merge moved.
 *
 * @param targetId the person that survived
 * @param targetName their display name
 * @param duplicateId the person that was absorbed and deleted
 * @param namesMoved how many of the duplicate's names were kept as alternates
 * @param unionsMoved how many unions were repointed at the surviving person
 * @param unionsCollapsed how many unions became identical after repointing and were folded together
 * @param childLinksMoved how many parent/child links were repointed
 * @param eventsMoved how many events were repointed
 * @param citationsMoved how many citations were repointed
 * @param mediaMoved how many photos and scans were repointed
 * @param notes what was dropped rather than moved, one line each
 */
public record MergeResponse(
        Long targetId,
        String targetName,
        Long duplicateId,
        int namesMoved,
        int unionsMoved,
        int unionsCollapsed,
        int childLinksMoved,
        int eventsMoved,
        int citationsMoved,
        int mediaMoved,
        List<String> notes) {
}
