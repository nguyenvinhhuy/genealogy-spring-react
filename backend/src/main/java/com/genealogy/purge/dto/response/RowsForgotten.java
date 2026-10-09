package com.genealogy.purge.dto.response;

/**
 * What was deleted from the tables that name a union or a grave by id, which no foreign key reaches.
 *
 * @param events how many events were deleted
 * @param citations how many citations were deleted, those of the events included
 * @param files how many photos and scans were deleted
 */
public record RowsForgotten(int events, int citations, int files) {
}
