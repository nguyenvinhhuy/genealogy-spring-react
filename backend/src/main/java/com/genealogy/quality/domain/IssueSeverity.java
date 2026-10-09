package com.genealogy.quality.domain;

/** How seriously a data-quality finding should be taken. */
public enum IssueSeverity {

    // Impossible rather than merely unusual; only a graph cycle reaches this.
    ERROR,

    // Almost certainly a mistake, but a real gia phả occasionally proves otherwise.
    WARNING,

    // Worth noticing but entirely normal, so it is never presented as a problem.
    INFO
}
