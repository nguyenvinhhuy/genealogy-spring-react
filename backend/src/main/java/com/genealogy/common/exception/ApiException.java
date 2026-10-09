package com.genealogy.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base class for errors that map directly onto an RFC 9457 ProblemDetail. */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    /**
     * Creates an API exception carrying the HTTP status and title to render.
     *
     * @param status HTTP status for the response
     * @param title short, human-readable summary of the problem type
     * @param detail explanation of this specific occurrence
     */
    protected ApiException(HttpStatus status, String title, String detail) {
        this(status, title, detail, null);
    }

    /**
     * Creates an API exception that also carries the failure behind it, for the log and never for the client.
     *
     * @param status HTTP status for the response
     * @param title short, human-readable summary of the problem type
     * @param detail explanation of this specific occurrence
     * @param cause the underlying failure, or null
     */
    protected ApiException(HttpStatus status, String title, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.title = title;
    }
}
