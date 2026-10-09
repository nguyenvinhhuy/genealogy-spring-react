package com.genealogy.source.mapper;

import com.genealogy.source.domain.Citation;
import com.genealogy.source.domain.Source;
import com.genealogy.source.dto.request.CitationRequest;
import com.genealogy.source.dto.request.SourceRequest;
import com.genealogy.source.dto.response.CitationResponse;
import com.genealogy.source.dto.response.SourceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps source and citation entities to and from their DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SourceMapper {

    /**
     * Converts a source entity to its response, with its citation count counted by the caller.
     *
     * @param source the entity
     * @param citationCount how often it has been cited
     * @return the response DTO
     */
    @Mapping(target = "citationCount", source = "citationCount")
    SourceResponse toResponse(Source source, long citationCount);

    /**
     * Builds a new source entity from a request.
     *
     * @param request the inbound payload
     * @return the entity, without identity, author or timestamps
     */
    // The version is Hibernate's to set: the one a form sends is only compared, by StaleEdit.
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "type", defaultValue = "OTHER")
    Source toEntity(SourceRequest request);

    /**
     * Copies a request onto an existing source entity.
     *
     * @param request the inbound payload
     * @param source the entity to update
     */
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "type", defaultValue = "OTHER")
    void update(SourceRequest request, @MappingTarget Source source);

    /**
     * Converts a citation entity to its response, inlining enough of its source to render.
     *
     * @param citation the entity
     * @param source the source it cites, or null when that source is gone
     * @return the response DTO
     */
    @Mapping(target = "id", source = "citation.id")
    @Mapping(target = "version", source = "citation.version")
    @Mapping(target = "sourceTitle", source = "source.title")
    @Mapping(target = "sourceType", source = "source.type")
    CitationResponse toResponse(Citation citation, Source source);

    /**
     * Copies a request's locator and quote onto a citation entity; the source and the target are the caller's.
     *
     * @param request the inbound payload
     * @param citation the entity to update
     */
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "sourceId", ignore = true)
    @Mapping(target = "targetType", ignore = true)
    @Mapping(target = "targetId", ignore = true)
    void update(CitationRequest request, @MappingTarget Citation citation);
}
