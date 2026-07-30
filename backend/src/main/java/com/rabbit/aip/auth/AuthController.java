package com.rabbit.aip.auth;

import com.rabbit.aip.auth.AuthDtos.AuthResponse;
import com.rabbit.aip.auth.AuthDtos.LoginRequest;
import com.rabbit.aip.auth.AuthDtos.LogoutRequest;
import com.rabbit.aip.auth.AuthDtos.MeResponse;
import com.rabbit.aip.auth.AuthDtos.RefreshRequest;
import com.rabbit.aip.auth.AuthDtos.SelectOrganisationRequest;
import com.rabbit.aip.auth.InvitationDtos.ActivateInvitationRequest;
import com.rabbit.aip.auth.InvitationDtos.ActivationResponse;
import com.rabbit.aip.auth.InvitationDtos.InvitationDetails;
import com.rabbit.aip.auth.InvitationDtos.InvitationTokenRequest;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.organisation.Organisation;
import com.rabbit.aip.organisation.OrganisationRepository;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.user.UserAccount;
import com.rabbit.aip.user.UserAccountRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final InvitationService invitationService;
    private final CurrentSession session;
    private final UserAccountRepository users;
    private final OrganisationRepository organisations;

    public AuthController(
            AuthService authService,
            InvitationService invitationService,
            CurrentSession session,
            UserAccountRepository users,
            OrganisationRepository organisations
    ) {
        this.authService = authService;
        this.invitationService = invitationService;
        this.session = session;
        this.users = users;
        this.organisations = organisations;
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/select-organisation")
    AuthResponse select(@Valid @RequestBody SelectOrganisationRequest request) {
        return authService.selectOrganisation(
                request.selectionToken(),
                request.organisationId()
        );
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/validate")
    InvitationDetails validateInvitation(
            @Valid @RequestBody InvitationTokenRequest request
    ) {
        return invitationService.validate(request.token());
    }

    @PostMapping("/invitations/activate")
    ActivationResponse activateInvitation(
            @Valid @RequestBody ActivateInvitationRequest request
    ) {
        return invitationService.activate(request.token(), request.password());
    }

    @GetMapping("/me")
    MeResponse me() {
        UserAccount user = users.findById(session.userId())
                .orElseThrow(() -> DomainException.notFound(
                        "USER_NOT_FOUND",
                        "The signed-in account no longer exists."
                ));
        Organisation organisation = organisations.findById(session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "ORGANISATION_NOT_FOUND",
                        "The selected organisation no longer exists."
                ));
        return new MeResponse(
                session.userId(),
                session.email(),
                user.getFirstName(),
                user.getLastName(),
                session.organisationId(),
                organisation.getCode(),
                organisation.getName(),
                organisation.getTimezone(),
                session.role()
        );
    }
}
