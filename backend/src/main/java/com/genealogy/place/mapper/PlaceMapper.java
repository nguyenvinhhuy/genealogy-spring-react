package com.genealogy.place.mapper;

import com.genealogy.place.domain.Place;
import com.genealogy.place.dto.request.PlaceRequest;
import com.genealogy.place.dto.response.PlaceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps place entities to and from their DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlaceMapper {

    /**
     * Converts a place entity to its response, with its path rendered by the caller.
     *
     * @param place the entity
     * @param path the place and its ancestors, most specific first
     * @return the response DTO
     */
    @Mapping(target = "path", source = "path")
    PlaceResponse toResponse(Place place, String path);

    /**
     * Builds a new place entity from a request.
     *
     * @param request the inbound payload
     * @return the entity, without identity or timestamps
     */
    // The version is Hibernate's to set: the one a form sends is only compared, by StaleEdit.
    @Mapping(target = "version", ignore = true)
    Place toEntity(PlaceRequest request);

    /**
     * Copies a request onto an existing place entity.
     *
     * @param request the inbound payload
     * @param place the entity to update
     */
    @Mapping(target = "version", ignore = true)
    void update(PlaceRequest request, @MappingTarget Place place);
}
