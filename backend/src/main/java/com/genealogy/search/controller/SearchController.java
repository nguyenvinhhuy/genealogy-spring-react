package com.genealogy.search.controller;

import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.person.dto.response.PersonSummaryResponse;
import com.genealogy.search.dto.request.PersonSearchRequest;
import com.genealogy.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for searching the gia phả across every recorded field. */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * Finds the people matching every filter that was given.
     *
     * @param request the filters, bound from the query string
     * @param principal the authenticated caller
     * @param pageable paging information
     * @return the matching page
     */
    @GetMapping("/persons")
    @Operation(summary = "Search people by name, chi, đời, dates and place")
    public PagedModel<PersonSummaryResponse> searchPersons(
            @Valid PersonSearchRequest request,
            @AuthenticationPrincipal AuthPrincipal principal,
            Pageable pageable) {
        return new PagedModel<>(searchService.searchPersons(request, principal.role(), pageable));
    }
}
