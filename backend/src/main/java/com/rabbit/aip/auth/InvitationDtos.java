package com.rabbit.aip.auth;

import com.rabbit.aip.user.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class InvitationDtos {

    private InvitationDtos() {
    }

    public record InvitationTokenRequest(
            @NotBlank @Size(max = 256) String token
    ) {
    }

    public record ActivateInvitationRequest(
            @NotBlank @Size(max = 256) String token,
            @NotBlank
            @Size(min = 12, max = 72)
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                    message = "Password must include uppercase, lowercase, number, and symbol."
            )
            String password
    ) {
    }

    public record InvitationDetails(
            String email,
            String firstName,
            String lastName,
            String organisationName,
            UserRole role,
            Instant expiresAt
    ) {
    }

    public record ActivationResponse(
            boolean activated,
            String email,
            String organisationName
    ) {
    }
}
