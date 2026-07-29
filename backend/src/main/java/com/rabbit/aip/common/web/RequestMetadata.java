package com.rabbit.aip.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestMetadata {

    public String ipAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) return "system";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return Optional.ofNullable(request.getRemoteAddr()).orElse("unknown");
    }

    public String traceId() {
        return Optional.ofNullable(MDC.get("traceId")).orElse("unavailable");
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
