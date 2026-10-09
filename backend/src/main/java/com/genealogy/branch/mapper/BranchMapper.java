package com.genealogy.branch.mapper;

import com.genealogy.branch.domain.Branch;
import com.genealogy.branch.dto.request.BranchRequest;
import com.genealogy.branch.dto.response.BranchResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps clan-branch entities to and from their DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BranchMapper {

    /**
     * Converts a branch entity to its response representation.
     *
     * @param branch the entity
     * @return the response DTO
     */
    BranchResponse toResponse(Branch branch);

    /**
     * Builds a new branch entity from a request.
     *
     * @param request the inbound payload
     * @return the entity, without identity or timestamps
     */
    // The version is Hibernate's to set: the one a form sends is only compared, by StaleEdit.
    @Mapping(target = "version", ignore = true)
    Branch toEntity(BranchRequest request);

    /**
     * Copies a request onto an existing branch entity.
     *
     * @param request the inbound payload
     * @param branch the entity to update
     */
    @Mapping(target = "version", ignore = true)
    void update(BranchRequest request, @MappingTarget Branch branch);
}
