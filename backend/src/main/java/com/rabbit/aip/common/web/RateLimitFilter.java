package com.rabbit.aip.common.web;

import com.rabbit.aip.common.web.FixedWindowRateLimiter.Decision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService limiter;
    private final RequestMetrics metrics;

    public RateLimitFilter(
            RateLimitService limiter,
            RequestMetrics metrics
    ) {
        this.limiter = limiter;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health")
                || request.getRequestURI().equals("/actuator/info");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        boolean authenticated = authentication instanceof JwtAuthenticationToken;
        String identity = authenticated
                ? authenticatedIdentity((JwtAuthenticationToken) authentication)
                : "anonymous:" + clientAddress(request);
        putLoggingContext(authentication);
        boolean strictAnonymous = !authenticated
                && request.getRequestURI().equals("/api/v1/auth/login");
        Decision decision = limiter.acquire(identity, authenticated || !strictAnonymous);
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader(
                "X-RateLimit-Reset",
                String.valueOf(decision.resetsAt().getEpochSecond())
        );
        try {
            if (!decision.allowed()) {
                metrics.recordRateLimited();
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Retry-After", String.valueOf(Math.max(
                        1, decision.resetsAt().getEpochSecond() - Instant.now().getEpochSecond()
                )));
                response.getWriter().write("""
                        {"timestamp":"%s","status":429,"code":"RATE_LIMIT_EXCEEDED",\
"message":"Too many requests. Please wait before trying again.",\
"path":"%s","traceId":"%s"}
                        """.formatted(
                        Instant.now(),
                        json(request.getRequestURI()),
                        json(MDC.get("traceId"))
                ).trim());
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("orgId");
            MDC.remove("userId");
        }
    }

    private String authenticatedIdentity(JwtAuthenticationToken token) {
        String organisationId = token.getToken().getClaimAsString("org_id");
        return "authenticated:"
                + (organisationId == null ? "unselected" : organisationId)
                + ":" + token.getToken().getSubject();
    }

    private void putLoggingContext(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) return;
        String organisationId = token.getToken().getClaimAsString("org_id");
        if (organisationId != null) MDC.put("orgId", organisationId);
        MDC.put("userId", token.getToken().getSubject());
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || forwarded.length() > 256) {
            return request.getRemoteAddr();
        }
        return forwarded.split(",", 2)[0].trim();
    }

    private String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
