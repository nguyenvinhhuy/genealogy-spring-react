package com.genealogy.person.mapper;

import com.genealogy.person.domain.Person;
import com.genealogy.person.domain.PersonName;
import com.genealogy.person.dto.request.PersonNameRequest;
import com.genealogy.person.dto.request.PersonRequest;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.dto.response.PersonNameResponse;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.dto.response.PersonSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps person entities to and from their DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PersonMapper {

    /**
     * Converts a person to its list representation.
     *
     * @param person the entity
     * @return the summary DTO
     */
    @Mapping(target = "displayName", expression = "java(displayName(person))")
    PersonSummaryResponse toSummary(Person person);

    /**
     * Converts a person to the node projection the read-across features draw from.
     *
     * @param person the entity
     * @return the node DTO
     */
    @Mapping(target = "displayName", expression = "java(displayName(person))")
    PersonNodeResponse toNode(Person person);

    /**
     * Converts a person to its full representation.
     *
     * @param person the entity
     * @return the detail DTO
     */
    @Mapping(target = "displayName", expression = "java(displayName(person))")
    PersonDetailResponse toDetail(Person person);

    /**
     * Converts one name to its response representation.
     *
     * @param name the entity
     * @return the name DTO
     */
    @Mapping(target = "display", expression = "java(name.display())")
    PersonNameResponse toNameResponse(PersonName name);

    /**
     * Builds a name entity from a request.
     *
     * @param request the inbound payload
     * @return the entity, without identity or timestamps
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    // Blank parts stored as null: search and V9's index read '' as a part and found " Văn An" (§8.10 #31).
    @Mapping(target = "surname", expression = "java(com.genealogy.common.util.Blank.toNull(request.surname()))")
    @Mapping(target = "middleName", expression = "java(com.genealogy.common.util.Blank.toNull(request.middleName()))")
    @Mapping(target = "givenName", expression = "java(com.genealogy.common.util.Blank.toNull(request.givenName()))")
    PersonName toNameEntity(PersonNameRequest request);

    /**
     * Copies a request's sex, chi and notes onto a person, leaving names and every derived field alone.
     *
     * @param request the inbound payload
     * @param person the entity to update in place
     */
    // An omitted sex is recorded as UNKNOWN, never left as whatever was there: the form always sends the full record.
    @Mapping(target = "gender", source = "gender", defaultValue = "UNKNOWN")
    @Mapping(target = "names", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "generation", ignore = true)
    @Mapping(target = "living", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void applyFields(PersonRequest request, @MappingTarget Person person);

    /**
     * Converts a person back into the request that would recreate them as they stand, for a suggestion to amend.
     *
     * @param person the entity
     * @return the request, with no change note and no version
     */
    @Mapping(target = "changeNote", ignore = true)
    @Mapping(target = "version", ignore = true)
    PersonRequest toRequest(Person person);

    /**
     * Converts one name back into its request form.
     *
     * @param name the entity
     * @return the name request
     */
    PersonNameRequest toNameRequest(PersonName name);

    /**
     * Copies a name as a new alternate name, for a merge that keeps the survivor's own primary.
     *
     * @param name the name to copy
     * @return a new entity, never primary, without identity or timestamps
     */
    // Never primary: uq_person_names_one_primary allows one, and the survivor's own is the one kept.
    @Mapping(target = "primary", constant = "false")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PersonName copyAsAlternate(PersonName name);

    /**
     * Copies a name request onto an existing name entity, keeping its identity and timestamps.
     *
     * @param request the inbound payload
     * @param name the entity to update in place
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "surname", expression = "java(com.genealogy.common.util.Blank.toNull(request.surname()))")
    @Mapping(target = "middleName", expression = "java(com.genealogy.common.util.Blank.toNull(request.middleName()))")
    @Mapping(target = "givenName", expression = "java(com.genealogy.common.util.Blank.toNull(request.givenName()))")
    void updateNameEntity(PersonNameRequest request, @MappingTarget PersonName name);

    /**
     * Renders a person's primary name, falling back to the first recorded name.
     *
     * @param person the entity
     * @return the display name, or an empty string when the person has no names
     */
    default String displayName(Person person) {
        return person.primaryName()
                .or(() -> person.getNames().stream().findFirst())
                .map(PersonName::display)
                .orElse("");
    }
}
