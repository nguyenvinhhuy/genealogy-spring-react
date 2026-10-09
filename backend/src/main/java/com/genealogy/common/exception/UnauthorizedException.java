package com.genealogy.common.exception;

import org.springframework.http.HttpStatus;

/** Signals a unauthorized condition. */
public class UnauthorizedException extends ApiException {

    /**
     * Creates the exception with the given explanation.
     *
     * @param detail explanation of this specific occurrence
     */
    public UnauthorizedException(String detail) {
        super(HttpStatus.UNAUTHORIZED, ProblemMessages.TITLE_UNAUTHORIZED, detail);
    }
}
