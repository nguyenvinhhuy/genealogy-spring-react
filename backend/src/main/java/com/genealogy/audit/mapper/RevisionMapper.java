package com.genealogy.audit.mapper;

import com.genealogy.audit.domain.Revision;
import com.genealogy.audit.dto.response.RevisionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Maps audit-trail entities to their DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RevisionMapper {

    /**
     * Converts a revision to its response, with its author's name looked up by the caller.
     *
     * @param revision the entity
     * @param changedByName the author's name, or null when the account is gone or none was recorded
     * @return the response DTO
     */
    @Mapping(target = "changedByName", source = "changedByName")
    RevisionResponse toResponse(Revision revision, String changedByName);
}
