package com.genealogy.common.config;

import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

/** Builds the JSON converter the security handlers use to render ProblemDetail. */
public final class HttpMessageConverterConfig {

    /** Not instantiable. */
    private HttpMessageConverterConfig() {
    }

    /**
     * Builds a JSON message converter for writing a ProblemDetail straight to the servlet response.
     *
     * @return a new JSON message converter
     */
    public static HttpMessageConverter<Object> problemDetailConverter() {
        // Deliberately not a bean: Boot prepends context converters, and this one then claimed springdoc's byte[].
        return new JacksonJsonHttpMessageConverter();
    }
}
