package com.genealogy.common.security;

import com.genealogy.common.config.CorsProperties;
import jakarta.servlet.DispatcherType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Central authorization rules; every route's access level is decided here. */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/actuator/health",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    // Whole-clan reads that cannot be redacted without lying about what they contain (§3.6).
    private static final String[] READER_ONLY_PATHS = {
        "/api/v1/gedcom/export",
        "/api/v1/book",
        "/api/v1/revisions",
        "/api/v1/quality/**",
        // A MEMBER still sees a citation, source title included, through /citations (§8.8 D3).
        "/api/v1/sources/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;

    /**
     * Builds the stateless filter chain and its route authorization rules.
     *
     * @param http the security builder
     * @return the configured filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // The container's /error dispatch carries no token; denying it turned a real 4xx into 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Method-less: listing every account's email is the trưởng tộc's, as creating one is.
                        .requestMatchers("/api/v1/members").hasRole("ADMIN")
                        // Own password: anyone signed in, or a MEMBER could never change theirs at all.
                        .requestMatchers(HttpMethod.POST, "/api/v1/members/me/password").authenticated()
                        // Someone else's: ADMIN only, or an EDITOR resets the ADMIN's and takes the clan.
                        .requestMatchers(HttpMethod.POST, "/api/v1/members/*/password").hasRole("ADMIN")
                        // Disabling or re-roling an account is the trưởng tộc's; the blanket PUT rule lets EDITOR in.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/members/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/gedcom/import").hasRole("ADMIN")
                        // Anyone signed in may offer a suggestion (F17); only ADMIN and EDITOR may accept.
                        .requestMatchers(HttpMethod.POST, "/api/v1/suggestions").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/suggestions/*/review")
                        .hasAnyRole("ADMIN", "EDITOR")
                        // Deleting is the trưởng tộc's alone; this comes before the blanket write rules.
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMIN")
                        // A merge deletes a person, which an EDITOR may not do; the blanket POST rule would.
                        .requestMatchers(HttpMethod.POST, "/api/v1/merges/**").hasRole("ADMIN")
                        // A source merge deletes the folded source, and every delete is ADMIN's.
                        .requestMatchers(HttpMethod.POST, "/api/v1/sources/merge").hasRole("ADMIN")
                        // Method-less, so HEAD is covered; before the blanket writes, so it decides a POST too (#35).
                        .requestMatchers(READER_ONLY_PATHS).hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/**").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/**").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/**").hasAnyRole("ADMIN", "EDITOR")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Keeps the servlet container from registering the JWT filter a second time outside the security chain.
     *
     * @param filter the JWT filter bean
     * @return the disabled registration
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        // A @Component filter is auto-registered by Boot as well; it belongs only where addFilterBefore puts it.
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Restricts browser origins to the configured allow-list.
     *
     * @return the CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Supplies the password hashing algorithm used for member credentials.
     *
     * @return the BCrypt encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
