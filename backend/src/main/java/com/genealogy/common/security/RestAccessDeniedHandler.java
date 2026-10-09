package com.genealogy.common.security;

import com.genealogy.common.config.HttpMessageConverterConfig;
import com.genealogy.common.exception.ProblemMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Renders RFC 9457 for authenticated requests that lack the required role. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final HttpMessageConverter<Object> messageConverter =
            HttpMessageConverterConfig.problemDetailConverter();

    /**
     * Writes a 403 problem detail for a forbidden request.
     *
     * @param request the rejected request
     * @param response the outbound response
     * @param accessDeniedException the rejection cause
     * @throws IOException if the response cannot be written
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        // Same reason as the entry point: this never reaches GlobalExceptionHandler (CLAUDE.md 4.1).
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ProblemMessages.FORBIDDEN);
        problem.setTitle(ProblemMessages.TITLE_FORBIDDEN);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        messageConverter.write(problem, MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
    }
}
