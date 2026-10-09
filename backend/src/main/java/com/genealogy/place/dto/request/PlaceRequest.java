package com.genealogy.place.dto.request;

import com.genealogy.common.model.PlaceType;
import com.genealogy.common.util.ChangeNotes;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload for creating or updating a place.
 *
 * @param name place name
 * @param type administrative level
 * @param parentId enclosing place id, or null
 * @param latitude latitude in degrees, or null; must be given together with longitude
 * @param longitude longitude in degrees, or null; must be given together with latitude
 * @param changeNote why this edit was made — the audit trail's "on what basis" (§3.8)
 * @param version the version the edit was made from, or null to skip the stale-edit check
 */
public record PlaceRequest(
        @NotBlank @Size(max = MAX_NAME) String name,
        @NotNull PlaceType type,
        Long parentId,
        @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
        Long version) {

    // Longest name the column holds, named so a GEDCOM path can be trimmed to it before saving (§8.8 #4).
    public static final int MAX_NAME = 200;
}
