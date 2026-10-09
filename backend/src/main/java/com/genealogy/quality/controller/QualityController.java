package com.genealogy.quality.controller;

import com.genealogy.quality.dto.response.QualityIssueResponse;
import com.genealogy.quality.service.QualityService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for data-quality findings. */
@RestController
@RequestMapping("/api/v1/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityService qualityService;

    /**
     * Runs every consistency rule over the whole clan.
     *
     * @return the findings, most serious first
     */
    @GetMapping("/issues")
    @Operation(summary = "List data-quality findings")
    public List<QualityIssueResponse> issues() {
        return qualityService.checkAll();
    }
}
