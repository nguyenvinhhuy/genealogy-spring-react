package com.genealogy.source.dto.response;

import java.util.List;

/**
 * What moving one record's citations onto another did.
 *
 * @param moved how many citations were repointed
 * @param folded one line per citation that duplicated one already on the kept record and was folded into it
 */
public record CitationsMoved(int moved, List<String> folded) {
}
