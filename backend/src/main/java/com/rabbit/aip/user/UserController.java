package com.rabbit.aip.user;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.academic.AcademicSectionRepository;
import com.rabbit.aip.academic.SectionStatus;
import com.rabbit.aip.auth.InvitationService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.commercial.CommercialService;
import com.rabbit.aip.commercial.CommercialTypes.Entitlement;
import com.rabbit.aip.security.CurrentSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final InvitationService invitations;
    private final AuditService audit;
    private final CommercialService commercial;
    private final AcademicSectionRepository sections;

    public UserController(
            UserAccountRepository users,
            OrganisationMembershipRepository memberships,
            CurrentSession session,
            InvitationService invitations,
            AuditService audit,
            CommercialService commercial,
            AcademicSectionRepository sections
    ) {
        this.users = users;
        this.memberships = memberships;
        this.session = session;
        this.invitations = invitations;
        this.audit = audit;
        this.commercial = commercial;
        this.sections = sections;
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
    InvitationResponse create(@Valid @RequestBody CreateUserRequest request) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        commercial.requireStudentCapacity(request.role());
        if (request.role() == UserRole.STUDENT && request.sectionId() == null) {
            throw DomainException.badRequest(
                    "SECTION_REQUIRED", "Students must be assigned to an active section."
            );
        }
        if (request.sectionId() != null
                && (request.role() == UserRole.STUDENT || request.role() == UserRole.FACULTY)) {
            sections.findByIdAndOrganisationId(request.sectionId(), session.organisationId())
                    .filter(section -> section.getStatus() == SectionStatus.ACTIVE)
                    .orElseThrow(() -> DomainException.badRequest(
                            "SECTION_NOT_ACTIVE",
                            "The selected section is not active in this organisation."
                    ));
        }
        InvitationService.IssuedInvitation issued = invitations.create(
                session.organisationId(),
                session.userId(),
                request.email(),
                request.firstName(),
                request.lastName(),
                request.role(),
                request.sectionId()
        );
        return InvitationResponse.from(issued);
    }

    @PostMapping("/{membershipId}/invitation")
    InvitationResponse reissueInvitation(@PathVariable UUID membershipId) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return InvitationResponse.from(invitations.reissue(
                session.organisationId(),
                session.userId(),
                membershipId
        ));
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
        if (before == AccountStatus.INVITED
                || request.status() == AccountStatus.INVITED) {
            throw DomainException.badRequest(
                    "INVITATION_ACTIVATION_REQUIRED",
                    "Invited users must activate their account using a valid invitation."
            );
        }
        if (membership.getRole() == UserRole.STUDENT
                && before != AccountStatus.ACTIVE
                && request.status() == AccountStatus.ACTIVE) {
            commercial.requireStudentCapacity(UserRole.STUDENT);
        }
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

    public record UserResponse(
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

    record InvitationResponse(
            UserResponse user,
            String activationUrl,
            Instant expiresAt
    ) {
        static InvitationResponse from(InvitationService.IssuedInvitation issued) {
            return new InvitationResponse(
                    UserResponse.from(issued.user(), issued.membership()),
                    issued.activationUrl(),
                    issued.expiresAt()
            );
        }
    }
}
