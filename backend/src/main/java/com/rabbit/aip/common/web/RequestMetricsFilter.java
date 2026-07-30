package com.rabbit.aip.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestMetricsFilter extends OncePerRequestFilter {

    private final RequestMetrics metrics;

    public RequestMetricsFilter(RequestMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            metrics.record(response.getStatus(), System.nanoTime() - started);
        }
    }
}
