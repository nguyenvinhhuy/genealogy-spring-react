package com.genealogy.family.mapper;

import com.genealogy.family.domain.Family;
import com.genealogy.family.domain.FamilyChild;
import com.genealogy.family.dto.request.FamilyRequest;
import com.genealogy.family.dto.response.ChildEdgeResponse;
import com.genealogy.family.dto.response.FamilyChildResponse;
import com.genealogy.family.dto.response.FamilyResponse;
import com.genealogy.family.dto.response.UnionEdgeResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps union entities to their DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FamilyMapper {

    /**
     * Converts a union and its child links to the response representation.
     *
     * @param family the entity
     * @param children the child links belonging to it
     * @return the response DTO
     */
    @Mapping(target = "id", source = "family.id")
    @Mapping(target = "children", source = "children")
    FamilyResponse toResponse(Family family, List<FamilyChild> children);

    /**
     * Converts one child link to its response representation.
     *
     * @param child the entity
     * @return the response DTO
     */
    FamilyChildResponse toChildResponse(FamilyChild child);

    /**
     * Converts one child link to the edge projection the read-across features walk.
     *
     * @param child the entity
     * @return the edge DTO
     */
    ChildEdgeResponse toChildEdge(FamilyChild child);

    /**
     * Converts a union and its already-converted child edges to the union projection.
     *
     * @param family the entity
     * @param children the union's child edges
     * @return the edge DTO
     */
    @Mapping(target = "familyId", source = "family.id")
    @Mapping(target = "children", source = "children")
    UnionEdgeResponse toUnionEdge(Family family, List<ChildEdgeResponse> children);

    /**
     * Copies a request's partners, status and position onto a union.
     *
     * @param request the inbound payload
     * @param family the entity to update in place
     */
    // An omitted status is a marriage and an omitted position the first union, as a new union's form leaves them.
    @Mapping(target = "status", source = "status", defaultValue = "MARRIED")
    @Mapping(target = "orderIndex", source = "orderIndex", defaultValue = "0")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void applyRequest(FamilyRequest request, @MappingTarget Family family);
}
