package com.rabbit.aip.auth;

import com.rabbit.aip.auth.AuthDtos.AuthResponse;
import com.rabbit.aip.auth.AuthDtos.LoginRequest;
import com.rabbit.aip.auth.AuthDtos.LogoutRequest;
import com.rabbit.aip.auth.AuthDtos.MeResponse;
import com.rabbit.aip.auth.AuthDtos.RefreshRequest;
import com.rabbit.aip.auth.AuthDtos.SelectOrganisationRequest;
import com.rabbit.aip.security.CurrentSession;
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
    private final CurrentSession session;

    public AuthController(AuthService authService, CurrentSession session) {
        this.authService = authService;
        this.session = session;
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

    @GetMapping("/me")
    MeResponse me() {
        return new MeResponse(
                session.userId(),
                session.email(),
                session.organisationId(),
                session.role()
        );
    }
}
