package com.genealogy.source.dto.response;

import java.util.List;

/**
 * What folding one source into another did.
 *
 * @param source the kept source, as it is now
 * @param citationsMoved how many citations were repointed onto it
 * @param mediaMoved how many scans were moved onto it
 * @param folded one line per citation that duplicated one the kept source already had and was folded into it
 */
public record SourceMergeResponse(SourceResponse source, int citationsMoved, int mediaMoved, List<String> folded) {
}
