package com.genealogy.grave.dto.request;

import com.genealogy.common.model.GraveKind;
import com.genealogy.common.util.ChangeNotes;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload for recording or replacing a mộ phần; every field is optional, and an omitted one is cleared.
 *
 * @param kind a grave (mộ) or a plot built for someone still alive (sinh phần); defaults to a grave
 * @param placeId the enclosing place, or null
 * @param plot free-text plot reference, e.g. "khu B, hàng 3, mộ 12"
 * @param latitude latitude in degrees; must be given together with longitude
 * @param longitude longitude in degrees; must be given together with latitude
 * @param notes free-text notes
 * @param changeNote why this edit was made — the audit trail's "on what basis" (§3.8)
 * @param version the version the edit was made from, or null to skip the stale-edit check
 */
public record GraveRequest(
        GraveKind kind,
        Long placeId,
        @Size(max = MAX_PLOT) String plot,
        @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        @Size(max = MAX_NOTES) String notes,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
        Long version) {

    // Longest plot reference the column holds, named so the GEDCOM import can trim to it before saving (§8.8 #4).
    public static final int MAX_PLOT = 200;

    // Longest note accepted; the column is TEXT, so this limit is the request's own.
    public static final int MAX_NOTES = 10000;
}
