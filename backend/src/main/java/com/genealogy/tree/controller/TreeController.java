package com.genealogy.tree.controller;

import com.genealogy.tree.domain.TreeDirection;
import com.genealogy.tree.dto.response.FounderResponse;
import com.genealogy.tree.dto.response.KinshipResponse;
import com.genealogy.tree.dto.response.SubgraphResponse;
import com.genealogy.tree.service.KinshipService;
import com.genealogy.tree.service.TreeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for drawing the family tree. */
@RestController
@RequestMapping("/api/v1/tree")
@RequiredArgsConstructor
@Validated
public class TreeController {

    private static final String DEFAULT_DEPTH = "3";

    private final TreeService treeService;
    private final KinshipService kinshipService;

    /**
     * Returns the slice of the gia phả around one person.
     *
     * @param focusId the person to centre on
     * @param direction which way to expand, defaults to descendants
     * @param depth how many generations to walk, clamped server-side
     * @return the subgraph
     */
    @GetMapping
    @Operation(summary = "Get the tree slice around one person")
    public SubgraphResponse subgraph(
            @RequestParam Long focusId,
            @RequestParam(defaultValue = "DESCENDANTS") TreeDirection direction,
            @RequestParam(defaultValue = DEFAULT_DEPTH) @Min(1) int depth) {
        return treeService.subgraph(focusId, direction, depth);
    }

    /**
     * Names the person the tree opens on when none is chosen.
     *
     * @return the clan's thuỷ tổ, or a null id when no union is recorded
     */
    @GetMapping("/founder")
    @Operation(summary = "Get the clan founder the tree starts from")
    public FounderResponse founder() {
        return treeService.founder();
    }

    /**
     * Names what one person is to another, in Vietnamese.
     *
     * @param fromId the speaker
     * @param toId the person being named
     * @return the relationship
     */
    @GetMapping("/kinship")
    @Operation(summary = "Work out the Vietnamese kinship term between two people")
    public KinshipResponse kinship(@RequestParam Long fromId, @RequestParam Long toId) {
        return kinshipService.describe(fromId, toId);
    }
}
