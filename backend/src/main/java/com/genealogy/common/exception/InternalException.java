package com.genealogy.common.exception;

import org.springframework.http.HttpStatus;

/** Signals a server-side failure whose message is worth showing to the caller. */
// Without it a broken font or a broken render answers "Unexpected server error", which names nothing.
public class InternalException extends ApiException {

    /**
     * Creates the exception with the given explanation and cause.
     *
     * @param detail explanation of this specific occurrence
     * @param cause what went wrong underneath
     */
    public InternalException(String detail, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ProblemMessages.TITLE_SERVER_ERROR, detail, cause);
    }
}
