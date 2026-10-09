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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Renders RFC 9457 for requests rejected before they reach a controller. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HttpMessageConverter<Object> messageConverter =
            HttpMessageConverterConfig.problemDetailConverter();

    /**
     * Writes a 401 problem detail for an unauthenticated request.
     *
     * @param request the rejected request
     * @param response the outbound response
     * @param authException the rejection cause
     * @throws IOException if the response cannot be written
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        // A rejection here never reaches GlobalExceptionHandler, so the ProblemDetail is built inline (CLAUDE.md 4.1).
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ProblemMessages.UNAUTHORIZED);
        problem.setTitle(ProblemMessages.TITLE_UNAUTHORIZED);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        messageConverter.write(problem, MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
    }
}
