package com.genealogy.merge.service;

import com.genealogy.merge.dto.request.MergeRequest;
import com.genealogy.merge.dto.response.MergeResponse;
import com.genealogy.source.dto.request.SourceMergeRequest;
import com.genealogy.source.dto.response.SourceMergeResponse;

/** Folds a duplicate person, or a duplicate source, into the one being kept (docs/analysis.md F11). */
public interface MergeService {

    /**
     * Folds one source into another: its citations and its scans move onto the kept source, and it is deleted.
     *
     * @param request which source is folded into which, and why
     * @param actorId id of the member running the merge
     * @return the kept source and what moved
     */
    SourceMergeResponse mergeSources(SourceMergeRequest request, Long actorId);

    /**
     * Merges one person into another and deletes the duplicate.
     *
     * @param request which two people, and on what basis
     * @param actorId id of the member running the merge
     * @return what was moved, folded or dropped
     */
    MergeResponse mergePersons(MergeRequest request, Long actorId);
}
