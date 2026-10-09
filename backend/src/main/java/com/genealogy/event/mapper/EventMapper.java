package com.genealogy.event.mapper;

import com.genealogy.common.model.GenealogyDate;
import com.genealogy.event.domain.Event;
import com.genealogy.event.dto.request.GenealogyDateRequest;
import com.genealogy.event.dto.response.EventFactResponse;
import com.genealogy.event.dto.response.EventResponse;
import com.genealogy.event.dto.response.GenealogyDateResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Maps event entities to and from their DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventMapper {

    /**
     * Flattens a dated event to the year and the span its date allows, for whole-graph analysis.
     *
     * @param event the entity, which must carry a year
     * @return the fact
     */
    @Mapping(target = "year", expression = "java(event.getDate().getYear())")
    @Mapping(target = "earliest", expression = "java(event.getDate().earliestPossible())")
    @Mapping(target = "latest", expression = "java(event.getDate().latestPossible())")
    EventFactResponse toFact(Event event);

    /**
     * Converts an event entity to its response representation.
     *
     * @param event the entity
     * @return the response DTO
     */
    EventResponse toResponse(Event event);

    /**
     * Converts a stored date to its response representation.
     *
     * @param date the embedded value
     * @return the response DTO
     */
    // Rendered once on the server: the book and the web page each had their own renderer, and they disagreed.
    @Mapping(target = "display", expression = "java(com.genealogy.common.model.GenealogyDateText.of(date))")
    GenealogyDateResponse toDateResponse(GenealogyDate date);

    /**
     * Builds a stored date from a request, leaving the derived sort date to the service.
     *
     * @param request the inbound payload
     * @return the embeddable value
     */
    @Mapping(target = "sortDate", ignore = true)
    GenealogyDate toDate(GenealogyDateRequest request);
}
