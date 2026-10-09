package com.genealogy.common.exception;

import org.springframework.http.HttpStatus;

/** Signals that a caller must wait before trying again, such as after repeated failed sign-ins. */
public class TooManyRequestsException extends ApiException {

    /**
     * Creates the exception with the given explanation.
     *
     * @param detail explanation of this specific occurrence
     */
    public TooManyRequestsException(String detail) {
        super(HttpStatus.TOO_MANY_REQUESTS, ProblemMessages.TITLE_TOO_MANY_REQUESTS, detail);
    }
}
