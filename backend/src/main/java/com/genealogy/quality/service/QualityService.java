package com.genealogy.quality.service;

import com.genealogy.quality.dto.response.QualityIssueResponse;
import java.util.List;

/** Checks the recorded gia phả for mistakes. */
public interface QualityService {

    /**
     * Runs every consistency rule over the whole clan, reporting warnings rather than blocking anything.
     *
     * @return the findings, most serious first
     */
    List<QualityIssueResponse> checkAll();
}
