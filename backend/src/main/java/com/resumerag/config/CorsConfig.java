package com.resumerag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CorsConfig {

    private final AppProperties appProperties;

    public CorsConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Exposed as a {@link CorsConfigurationSource} so Spring Security's own
     * CorsFilter picks it up (SecurityConfig enables {@code http.cors()}).
     *
     * <p>This matters: a browser preflight is an OPTIONS request with no
     * Authorization header, so a plain servlet CorsFilter registered at default
     * (lowest) precedence would run *after* the security filter chain and the
     * preflight would be rejected with 401 before CORS headers were ever added.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);

        config.setAllowedOriginPatterns(expandOrigins(appProperties.getCors().getAllowedOrigins()));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Builds the set of accepted origins from a comma-separated config value.
     *
     * <p>Exact-match only was a real trap. {@code http://localhost:3000} and
     * {@code http://127.0.0.1:3000} are different origins to a browser, as are
     * {@code http://[::1]:3000} and a LAN IP. When the configured origin did not
     * match, Spring rejected the preflight with a bare 403 and no
     * {@code Access-Control-Allow-Origin} header. The browser then hides the
     * response from JS entirely, so axios sees no {@code error.response} and
     * reports the generic "Network error. Please check your connection." — even
     * though the backend was up and reachable by curl.
     *
     * <p>So for every configured loopback origin we also accept its
     * {@code 127.0.0.1} and {@code [::1]} equivalents, which is safe because
     * those can only ever be the developer's own machine. Non-loopback origins
     * (production hosts) are passed through untouched and stay exact.
     */
    static List<String> expandOrigins(String origins) {
        List<String> result = new ArrayList<>();
        if (origins == null) {
            return result;
        }
        for (String raw : origins.split(",")) {
            String origin = raw.trim();
            if (origin.isEmpty()) {
                continue;
            }
            result.add(origin);
            for (String variant : loopbackVariants(origin)) {
                if (!result.contains(variant)) {
                    result.add(variant);
                }
            }
        }
        return result;
    }

    private static List<String> loopbackVariants(String origin) {
        String rest = null;
        if (origin.startsWith("http://localhost:")) {
            rest = origin.substring("http://localhost:".length());
        } else if (origin.startsWith("http://127.0.0.1:")) {
            rest = origin.substring("http://127.0.0.1:".length());
        } else if (origin.startsWith("http://[::1]:")) {
            rest = origin.substring("http://[::1]:".length());
        }
        if (rest == null) {
            return List.of();
        }
        return List.of("http://localhost:" + rest, "http://127.0.0.1:" + rest, "http://[::1]:" + rest);
    }
}
