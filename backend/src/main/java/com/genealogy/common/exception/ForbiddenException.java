package com.genealogy.common.exception;

import org.springframework.http.HttpStatus;

/** Signals that the caller is signed in but may not do or see this. */
public class ForbiddenException extends ApiException {

    /**
     * Creates the exception with the given explanation.
     *
     * @param detail explanation of this specific occurrence
     */
    public ForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, ProblemMessages.TITLE_FORBIDDEN, detail);
    }
}
