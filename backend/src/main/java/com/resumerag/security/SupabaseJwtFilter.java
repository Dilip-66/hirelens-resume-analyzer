package com.resumerag.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Component
public class SupabaseJwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public SupabaseJwtFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/api/health") || path.startsWith("/api/auth")) {
            chain.doFilter(request, response);
            return;
        }

        // CORS preflights never carry an Authorization header; they are handled
        // by the CORS filter and must not be rejected here.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response, "Missing bearer token");
            return;
        }

        try {
            String token = header.substring(7);
            String userId = jwtUtil.extractUserId(token);
            if (userId == null || userId.isBlank()) {
                unauthorized(response, "Invalid token subject");
                return;
            }
            var authToken = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authToken);
            chain.doFilter(request, response);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            unauthorized(response, "Invalid or expired token");
        }
    }

    /**
     * Writes the 401 directly instead of using {@code response.sendError}.
     *
     * <p>{@code sendError} makes the container run an ERROR dispatch, and Spring
     * Security filters that dispatch too. The error dispatch re-enters the
     * authorization rules with no authentication, the URL is not in the
     * permitAll list, and {@code ExceptionTranslationFilter} answers with
     * {@code Http403ForbiddenEntryPoint} - so a missing or expired token
     * surfaced to the client as a bare <b>403 with an empty body</b> instead of
     * the 401 this filter intends. The user sees "request failed with status
     * 403" on an ordinary session expiry, with nothing to indicate that signing
     * in again is the fix, and no body to distinguish it from a CORS rejection.
     *
     * <p>Writing the status and body here avoids the dispatch entirely, and also
     * gives the client a readable message to show.
     */
    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", message)));
    }
}
