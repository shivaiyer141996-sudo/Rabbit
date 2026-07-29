package com.rabbit.aip.security;

import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.user.UserRole;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentSession {

    public UUID userId() {
        return UUID.fromString(jwt().getSubject());
    }

    public UUID organisationId() {
        String value = jwt().getClaimAsString("org_id");
        if (value == null) {
            throw new DomainException(
                    "ORGANISATION_NOT_SELECTED",
                    "Select an organisation before continuing.",
                    HttpStatus.UNAUTHORIZED
            );
        }
        return UUID.fromString(value);
    }

    public UserRole role() {
        return UserRole.valueOf(jwt().getClaimAsString("role"));
    }

    public String email() {
        return jwt().getClaimAsString("email");
    }

    private Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new DomainException(
                    "AUTHENTICATION_REQUIRED",
                    "Sign in to continue.",
                    HttpStatus.UNAUTHORIZED
            );
        }
        return token.getToken();
    }
}
