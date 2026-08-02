package com.rabbit.aip.auth;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.auth.InvitationDtos.ActivationResponse;
import com.rabbit.aip.auth.InvitationDtos.InvitationDetails;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.organisation.Organisation;
import com.rabbit.aip.organisation.OrganisationRepository;
import com.rabbit.aip.user.AccountStatus;
import com.rabbit.aip.user.OrganisationMembership;
import com.rabbit.aip.user.OrganisationMembershipRepository;
import com.rabbit.aip.user.UserAccount;
import com.rabbit.aip.user.UserAccountRepository;
import com.rabbit.aip.user.UserRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

    private final UserAccountRepository users;
    private final OrganisationMembershipRepository memberships;
    private final OrganisationRepository organisations;
    private final InvitationTokenRepository invitationTokens;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration invitationTtl;
    private final String activationBaseUrl;

    public InvitationService(
            UserAccountRepository users,
            OrganisationMembershipRepository memberships,
            OrganisationRepository organisations,
            InvitationTokenRepository invitationTokens,
            PasswordEncoder passwords,
            AuditService audit,
            Clock clock,
            @Value("${rabbit.security.invitation.ttl}") Duration invitationTtl,
            @Value("${rabbit.security.invitation.activation-base-url}")
            String activationBaseUrl
    ) {
        if (invitationTtl.isNegative() || invitationTtl.isZero()) {
            throw new IllegalArgumentException(
                    "Invitation lifetime must be greater than zero."
            );
        }
        this.users = users;
        this.memberships = memberships;
        this.organisations = organisations;
        this.invitationTokens = invitationTokens;
        this.passwords = passwords;
        this.audit = audit;
        this.clock = clock;
        this.invitationTtl = invitationTtl;
        this.activationBaseUrl = activationBaseUrl;
    }

    @Transactional
    public IssuedInvitation create(
            UUID organisationId,
            UUID createdByUserId,
            String email,
            String firstName,
            String lastName,
            UserRole role,
            UUID sectionId
    ) {
        String normalizedEmail = email.trim();
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new DomainException(
                    "EMAIL_ALREADY_EXISTS",
                    "Email must be unique across the platform.",
                    HttpStatus.CONFLICT
            );
        }
        UserAccount user = users.save(new UserAccount(
                normalizedEmail,
                passwords.encode(generateRawToken()),
                firstName,
                lastName,
                AccountStatus.INVITED,
                true
        ));
        OrganisationMembership membership = memberships.save(
                new OrganisationMembership(
                        organisationId,
                        user.getId(),
                        role,
                        AccountStatus.INVITED,
                        sectionId
                )
        );
        IssuedToken token = issueToken(
                organisationId,
                user.getId(),
                membership.getId(),
                createdByUserId
        );
        audit.recordForOrganisation(
                organisationId,
                "USR",
                "INVITE",
                "UserAccount",
                user.getId(),
                null,
                user.getEmail()
        );
        return new IssuedInvitation(user, membership, token.url(), token.expiresAt());
    }

    @Transactional
    public IssuedInvitation reissue(
            UUID organisationId,
            UUID createdByUserId,
            UUID membershipId
    ) {
        OrganisationMembership membership = memberships.findById(membershipId)
                .filter(item -> item.getOrganisationId().equals(organisationId))
                .orElseThrow(() -> DomainException.notFound(
                        "USER_NOT_FOUND",
                        "User membership was not found."
                ));
        UserAccount user = users.findById(membership.getUserId()).orElseThrow();
        requireInvited(user, membership);
        IssuedToken token = issueToken(
                organisationId,
                user.getId(),
                membership.getId(),
                createdByUserId
        );
        audit.recordForOrganisation(
                organisationId,
                "USR",
                "INVITATION_REISSUE",
                "OrganisationMembership",
                membership.getId(),
                "INVITED",
                token.expiresAt().toString()
        );
        return new IssuedInvitation(user, membership, token.url(), token.expiresAt());
    }

    @Transactional(readOnly = true)
    public InvitationDetails validate(String rawToken) {
        InvitationToken token = invitationTokens.findByTokenHash(hash(rawToken))
                .filter(item -> item.isUsable(clock.instant()))
                .orElseThrow(this::invalidInvitation);
        UserAccount user = users.findById(token.getUserId())
                .orElseThrow(this::invalidInvitation);
        OrganisationMembership membership = memberships.findById(token.getMembershipId())
                .orElseThrow(this::invalidInvitation);
        requireInvited(user, membership);
        Organisation organisation = organisations.findById(token.getOrganisationId())
                .orElseThrow(this::invalidInvitation);
        return new InvitationDetails(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                organisation.getName(),
                membership.getRole(),
                token.getExpiresAt()
        );
    }

    @Transactional
    public ActivationResponse activate(String rawToken, String password) {
        Instant now = clock.instant();
        InvitationToken token = invitationTokens
                .findByTokenHashForUpdate(hash(rawToken))
                .filter(item -> item.isUsable(now))
                .orElseThrow(this::invalidInvitation);
        UserAccount user = users.findByIdForUpdate(token.getUserId())
                .orElseThrow(this::invalidInvitation);
        OrganisationMembership membership = memberships
                .findByIdForUpdate(token.getMembershipId())
                .orElseThrow(this::invalidInvitation);
        if (!membership.getOrganisationId().equals(token.getOrganisationId())
                || !membership.getUserId().equals(user.getId())) {
            throw invalidInvitation();
        }
        requireInvited(user, membership);
        Organisation organisation = organisations.findById(token.getOrganisationId())
                .orElseThrow(this::invalidInvitation);

        user.activate(passwords.encode(password));
        membership.activateInvitation();
        token.consume(now);
        audit.recordAuthentication(
                organisation.getId(),
                user.getId(),
                user.getEmail(),
                membership.getRole(),
                "INVITATION_ACTIVATED",
                "INVITED",
                "ACTIVE"
        );
        return new ActivationResponse(true, user.getEmail(), organisation.getName());
    }

    private IssuedToken issueToken(
            UUID organisationId,
            UUID userId,
            UUID membershipId,
            UUID createdByUserId
    ) {
        String rawToken = generateRawToken();
        String tokenHash = hash(rawToken);
        Instant expiresAt = clock.instant().plus(invitationTtl);
        InvitationToken token = invitationTokens
                .findByMembershipIdForUpdate(membershipId)
                .map(existing -> {
                    existing.reissue(tokenHash, expiresAt, createdByUserId);
                    return existing;
                })
                .orElseGet(() -> new InvitationToken(
                        organisationId,
                        userId,
                        membershipId,
                        tokenHash,
                        expiresAt,
                        createdByUserId
                ));
        invitationTokens.save(token);
        return new IssuedToken(
                normalizedActivationBaseUrl() + "#token=" + rawToken,
                expiresAt
        );
    }

    private void requireInvited(
            UserAccount user,
            OrganisationMembership membership
    ) {
        if (user.getStatus() != AccountStatus.INVITED
                || membership.getStatus() != AccountStatus.INVITED) {
            throw invalidInvitation();
        }
    }

    private String normalizedActivationBaseUrl() {
        return activationBaseUrl.replaceAll("[/#]+$", "");
    }

    private String generateRawToken() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private DomainException invalidInvitation() {
        return new DomainException(
                "INVITATION_INVALID",
                "This invitation is invalid, expired, or already used.",
                HttpStatus.GONE
        );
    }

    public record IssuedInvitation(
            UserAccount user,
            OrganisationMembership membership,
            String activationUrl,
            Instant expiresAt
    ) {
    }

    private record IssuedToken(String url, Instant expiresAt) {
    }
}
