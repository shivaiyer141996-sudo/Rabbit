package com.rabbit.aip.auth;

import com.rabbit.aip.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record SelectOrganisationRequest(
            @NotBlank String selectionToken,
            @NotNull UUID organisationId
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record OrganisationChoice(
            UUID id,
            String code,
            String name,
            UserRole role,
            String logoDataUrl
    ) {
    }

    public record AuthResponse(
            boolean requiresOrganisationSelection,
            String selectionToken,
            List<OrganisationChoice> organisations,
            String accessToken,
            String refreshToken,
            long accessTokenExpiresIn,
            UserRole role,
            boolean firstLogin
    ) {
        public static AuthResponse selection(
                String token,
                List<OrganisationChoice> organisations
        ) {
            return new AuthResponse(
                    true, token, organisations, null, null, 0, null, false
            );
        }

        public static AuthResponse session(
                List<OrganisationChoice> organisations,
                String accessToken,
                String refreshToken,
                long expiresIn,
                UserRole role,
                boolean firstLogin
        ) {
            return new AuthResponse(
                    false,
                    null,
                    organisations,
                    accessToken,
                    refreshToken,
                    expiresIn,
                    role,
                    firstLogin
            );
        }
    }

    public record MeResponse(
            UUID userId,
            String email,
            String firstName,
            String lastName,
            UUID organisationId,
            String organisationCode,
            String organisationName,
            String timezone,
            boolean organisationLogoAvailable,
            java.time.Instant organisationLogoUpdatedAt,
            UserRole role
    ) {
    }
}
