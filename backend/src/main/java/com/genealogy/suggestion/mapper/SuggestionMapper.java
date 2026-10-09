package com.genealogy.suggestion.mapper;

import com.genealogy.suggestion.domain.Suggestion;
import com.genealogy.suggestion.dto.response.AnchorView;
import com.genealogy.suggestion.dto.response.PersonSide;
import com.genealogy.suggestion.dto.response.SuggestionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Maps suggestion entities to their DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SuggestionMapper {

    /**
     * Converts a suggestion to its response, with the names and the rendered proposal the caller looked up.
     *
     * @param suggestion the entity
     * @param targetName the display name of the person it is about, or null
     * @param createdByName the name of the member who offered it, or null
     * @param reviewedByName the name of the member who reviewed it, or null
     * @param proposal what was proposed, rendered, or null
     * @param anchor whom a new person would be added to, or null
     * @param payloadReadable whether the stored proposal could be read back
     * @return the response DTO
     */
    @Mapping(target = "kind", source = "suggestion.kind")
    @Mapping(target = "targetName", source = "targetName")
    @Mapping(target = "createdByName", source = "createdByName")
    @Mapping(target = "reviewedByName", source = "reviewedByName")
    @Mapping(target = "proposal", source = "proposal")
    @Mapping(target = "anchor", source = "anchor")
    @Mapping(target = "payloadReadable", source = "payloadReadable")
    SuggestionResponse toResponse(
            Suggestion suggestion,
            String targetName,
            String createdByName,
            String reviewedByName,
            PersonSide proposal,
            AnchorView anchor,
            boolean payloadReadable);
}
