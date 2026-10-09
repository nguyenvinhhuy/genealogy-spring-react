package com.genealogy.purge.dto.response;

/**
 * What deleting one person took with them.
 *
 * @param personId the person that was deleted
 * @param eventsDeleted how many of their events went
 * @param citationsDeleted how many citations of them went
 * @param mediaDeleted how many photos and scans went, objects included
 * @param suggestionsDeleted how many pending proposals about them went
 * @param unionsDeleted how many unions were left with no partner at all and were deleted
 */
public record PurgeResponse(
        Long personId,
        int eventsDeleted,
        int citationsDeleted,
        int mediaDeleted,
        int suggestionsDeleted,
        int unionsDeleted) {
}
