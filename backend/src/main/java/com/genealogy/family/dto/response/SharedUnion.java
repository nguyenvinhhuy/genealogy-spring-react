package com.genealogy.family.dto.response;

/**
 * A union that blocks a merge or a delete, and how many children are linked into it.
 *
 * @param familyId the union's id
 * @param childCount how many children are linked into it
 */
public record SharedUnion(Long familyId, int childCount) {
}
