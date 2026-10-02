package com.resumerag.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {

    @Test
    void acceptsLoopbackVariantsOfAConfiguredLocalhostOrigin() {
        List<String> origins = CorsConfig.expandOrigins("http://localhost:3000");

        // A browser treats these as three distinct origins. Rejecting any of them
        // produces a 403 with no Access-Control-Allow-Origin, which JS never sees,
        // which axios reports as a network error.
        assertTrue(origins.contains("http://localhost:3000"));
        assertTrue(origins.contains("http://127.0.0.1:3000"));
        assertTrue(origins.contains("http://[::1]:3000"));
    }

    @Test
    void keepsTheConfiguredPortWhenExpandingLoopbackVariants() {
        List<String> origins = CorsConfig.expandOrigins("http://localhost:5173");

        assertTrue(origins.contains("http://127.0.0.1:5173"));
        assertFalse(origins.contains("http://127.0.0.1:3000"));
    }

    @Test
    void expandsEveryOriginInACommaSeparatedList() {
        List<String> origins = CorsConfig.expandOrigins(
                "http://localhost:3000, https://hirelens.example.com");

        assertTrue(origins.contains("http://127.0.0.1:3000"));
        assertTrue(origins.contains("https://hirelens.example.com"));
    }

    @Test
    void doesNotRewriteNonLoopbackOrigins() {
        List<String> origins = CorsConfig.expandOrigins("https://hirelens.example.com");

        // Production hosts must stay exact - no wildcarding, no loopback guessing.
        assertTrue(origins.contains("https://hirelens.example.com"));
        assertFalse(origins.contains("http://127.0.0.1:3000"));
    }

    @Test
    void ignoresBlankEntriesAndNullConfig() {
        assertTrue(CorsConfig.expandOrigins(null).isEmpty());
        assertTrue(CorsConfig.expandOrigins("  ,  ").isEmpty());
    }
}
