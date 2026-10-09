package com.genealogy.member.mapper;

import com.genealogy.member.domain.Member;
import com.genealogy.member.dto.request.CreateMemberRequest;
import com.genealogy.member.dto.response.MemberResponse;
import com.genealogy.member.dto.response.MemberSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Maps member entities onto their response DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MemberMapper {

    /**
     * Converts a member entity to its response representation.
     *
     * @param member the entity
     * @return the response DTO
     */
    MemberResponse toResponse(Member member);

    /**
     * Converts a member entity to what the audit trail records of it.
     *
     * @param member the entity
     * @return the snapshot
     */
    MemberSnapshot toSnapshot(Member member);

    /**
     * Builds a new account from a create request, leaving the password and email to the service.
     *
     * @param request the account to create
     * @return the unsaved entity
     */
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    Member toEntity(CreateMemberRequest request);
}
