package com.genealogy.branch.dto.response;

/**
 * A clan branch as returned to clients.
 *
 * @param id branch id
 * @param name branch name
 * @param parentId parent branch id, or null
 * @param description free-text notes
 * @param version the row's version, sent back with an edit
 */
public record BranchResponse(Long id, String name, Long parentId, String description, long version) {
}
