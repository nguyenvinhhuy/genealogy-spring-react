package com.genealogy.grave.mapper;

import com.genealogy.grave.domain.Grave;
import com.genealogy.grave.dto.request.GraveRequest;
import com.genealogy.grave.dto.response.GraveResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps grave entities to and from their DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GraveMapper {

    /**
     * Converts a grave entity to its response representation.
     *
     * @param grave the entity
     * @return the response DTO
     */
    GraveResponse toResponse(Grave grave);

    /**
     * Copies a request onto a grave entity, replacing every field the request carries.
     *
     * @param request the inbound payload
     * @param grave the entity to update
     */
    // A full replace on purpose: the form sends every field, so a cleared field is a null the family meant.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "personId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "kind", source = "kind", defaultValue = "GRAVE")
    void update(GraveRequest request, @MappingTarget Grave grave);
}
