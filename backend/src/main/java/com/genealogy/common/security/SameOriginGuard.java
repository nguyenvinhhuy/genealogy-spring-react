package com.genealogy.common.security;

import com.genealogy.common.config.CorsProperties;
import com.genealogy.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** Refuses a cookie-authenticated request that a browser sent from a page this app does not serve. */
// CSRF is off because the API is token-based, but refresh and logout ride on the cookie alone (§8.10 #13).
@Component
@RequiredArgsConstructor
public class SameOriginGuard {

    // What Fetch Metadata calls a request from another site, or from a sibling subdomain SameSite=Lax lets through.
    private static final Set<String> FOREIGN_SITES = Set.of("cross-site", "same-site");

    private final CorsProperties corsProperties;

    /**
     * Throws a 403 when a browser request came from an origin other than the app's own.
     *
     * @param request the inbound request
     */
    public void requireOwnOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String site = request.getHeader("Sec-Fetch-Site");
        // A script with no Origin and no Fetch Metadata is not a browser, so it carries no victim's cookie by accident.
        boolean allowedOrigin = origin != null && corsProperties.allowedOrigins().contains(origin);
        boolean foreignOrigin = origin != null && !allowedOrigin;
        boolean foreignSite = site != null && FOREIGN_SITES.contains(site) && !allowedOrigin;
        if (foreignOrigin || foreignSite) {
            throw new ForbiddenException("Yêu cầu này phải được gửi từ chính trang gia phả.");
        }
    }
}
