package com.genealogy.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cross-origin settings bound from {@code app.cors}.
 *
 * @param allowedOrigins origins the browser may call the API from
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
