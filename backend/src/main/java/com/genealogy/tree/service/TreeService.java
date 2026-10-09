package com.genealogy.tree.service;

import com.genealogy.tree.domain.TreeDirection;
import com.genealogy.tree.dto.response.FounderResponse;
import com.genealogy.tree.dto.response.SubgraphResponse;

/** Reads bounded slices of the parentage graph for drawing. */
public interface TreeService {

    /**
     * Walks the graph outward from one person and returns what it reached.
     *
     * @param focusId the person to centre on
     * @param direction which way to expand
     * @param depth how many generations to walk, clamped to a safe maximum
     * @return the subgraph
     */
    SubgraphResponse subgraph(Long focusId, TreeDirection direction, int depth);

    /**
     * Names the person the tree opens on when none is chosen: the clan's thuỷ tổ.
     *
     * @return the founder, whose id may be null when no union is recorded
     */
    FounderResponse founder();
}
