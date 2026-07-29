package com.rabbit.aip.user;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.security.CurrentSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
public class UserController {

    private final UserAccountRepository users;
    private final OrganisationMembershipRepository memberships;
    private final CurrentSession session;
    private final PasswordEncoder passwords;
    private final AuditService audit;

    public UserController(
            UserAccountRepository users,
            OrganisationMembershipRepository memberships,
            CurrentSession session,
            PasswordEncoder passwords,
            AuditService audit
    ) {
        this.users = users;
        this.memberships = memberships;
        this.session = session;
        this.passwords = passwords;
        this.audit = audit;
    }

    @GetMapping
    List<UserResponse> list() {
        return memberships.findAllByOrganisationIdOrderByCreatedAtDesc(
                        session.organisationId()
                ).stream()
                .map(membership -> {
                    UserAccount user = users.findById(membership.getUserId())
                            .orElseThrow();
                    return UserResponse.from(user, membership);
                })
                .toList();
    }

    @PostMapping
    @Transactional
    UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        if (users.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new DomainException(
                    "EMAIL_ALREADY_EXISTS",
                    "Email must be unique across the platform.",
                    HttpStatus.CONFLICT
            );
        }
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        UserAccount user = users.save(new UserAccount(
                request.email(),
                passwords.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(random)),
                request.firstName(),
                request.lastName(),
                AccountStatus.INVITED,
                true
        ));
        OrganisationMembership membership = memberships.save(new OrganisationMembership(
                session.organisationId(),
                user.getId(),
                request.role(),
                AccountStatus.INVITED,
                request.sectionId()
        ));
        audit.record("USR", "CREATE", "UserAccount", user.getId(), null, user.getEmail());
        return UserResponse.from(user, membership);
    }

    @PatchMapping("/{membershipId}/status")
    @Transactional
    UserResponse status(
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        OrganisationMembership membership = memberships.findById(membershipId)
                .filter(item -> item.getOrganisationId().equals(session.organisationId()))
                .orElseThrow(() -> DomainException.notFound(
                        "USER_NOT_FOUND",
                        "User membership was not found."
                ));
        AccountStatus before = membership.getStatus();
        membership.setStatus(request.status());
        UserAccount user = users.findById(membership.getUserId()).orElseThrow();
        audit.record(
                "USR",
                "STATUS_CHANGE",
                "OrganisationMembership",
                membership.getId(),
                before.name(),
                request.status().name()
        );
        return UserResponse.from(user, membership);
    }

    record CreateUserRequest(
            @Email @NotBlank String email,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotNull UserRole role,
            UUID sectionId
    ) {
    }

    record UpdateStatusRequest(@NotNull AccountStatus status) {
    }

    record UserResponse(
            UUID userId,
            UUID membershipId,
            String email,
            String firstName,
            String lastName,
            UserRole role,
            AccountStatus status,
            UUID sectionId
    ) {
        static UserResponse from(
                UserAccount user,
                OrganisationMembership membership
        ) {
            return new UserResponse(
                    user.getId(),
                    membership.getId(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    membership.getRole(),
                    membership.getStatus(),
                    membership.getSectionId()
            );
        }
    }
}
