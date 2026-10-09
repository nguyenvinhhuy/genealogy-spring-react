package com.genealogy.grave.dto.response;

/**
 * A grave a merge had to delete.
 *
 * @param graveId the deleted grave's id, so the caller can forget the rows that named it
 * @param note what was lost, in words a trưởng tộc can act on
 */
public record DroppedGrave(Long graveId, String note) {
}
