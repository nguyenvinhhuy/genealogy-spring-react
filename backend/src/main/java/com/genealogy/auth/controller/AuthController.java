package com.genealogy.auth.controller;

import com.genealogy.auth.dto.request.LoginRequest;
import com.genealogy.auth.dto.response.LoginResponse;
import com.genealogy.auth.service.AuthService;
import com.genealogy.common.security.JwtProperties;
import com.genealogy.common.security.SameOriginGuard;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign-in, token rotation and sign-out endpoints. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final SameOriginGuard sameOriginGuard;

    /**
     * Signs a member in and sets the refresh cookie.
     *
     * @param request the presented credentials
     * @param http the servlet request, for the address the throttle counts against
     * @return the access token and the signed-in account
     */
    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Sign-in sets the refresh cookie, so a foreign page must not be able to sign a visitor in as someone else.
        sameOriginGuard.requireOwnOrigin(http);
        AuthService.AuthResult result = authService.login(request, http.getRemoteAddr());
        return respondWithCookie(result);
    }

    /**
     * Rotates the refresh cookie and returns a new access token.
     *
     * @param refreshToken the refresh cookie, absent when the client has none
     * @param http the servlet request, whose origin is checked
     * @return the new access token and the account
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh cookie for a new access token")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletRequest http) {
        // The cookie alone authenticates this, so a page on a sibling subdomain must not be able to send it.
        sameOriginGuard.requireOwnOrigin(http);
        AuthService.AuthResult result = authService.refresh(refreshToken);
        return respondWithCookie(result);
    }

    /**
     * Signs the caller out and clears the refresh cookie.
     *
     * @param refreshToken the refresh cookie, absent when the client has none
     * @param http the servlet request, whose origin is checked
     * @return an empty 204 response
     */
    @PostMapping("/logout")
    @Operation(summary = "Sign out and revoke the refresh token")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletRequest http) {
        sameOriginGuard.requireOwnOrigin(http);
        authService.logout(refreshToken);
        ResponseCookie cleared = buildCookie("", 0);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }

    /**
     * Returns the login body with the rotated refresh token attached as an HttpOnly cookie.
     *
     * @param result the completed authentication
     * @return the HTTP response
     */
    private ResponseEntity<LoginResponse> respondWithCookie(AuthService.AuthResult result) {
        ResponseCookie cookie =
                buildCookie(result.refreshToken(), jwtProperties.refreshTokenTtl().toSeconds());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result.response());
    }

    /**
     * Builds the refresh cookie with the given value and lifetime.
     *
     * @param value the cookie value, empty to clear it
     * @param maxAgeSeconds the cookie lifetime in seconds
     * @return the cookie
     */
    private static ResponseCookie buildCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
