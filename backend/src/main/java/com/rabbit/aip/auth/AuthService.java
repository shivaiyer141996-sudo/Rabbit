package com.rabbit.aip.auth;

import com.rabbit.aip.auth.AuthDtos.AuthResponse;
import com.rabbit.aip.auth.AuthDtos.OrganisationChoice;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.organisation.Organisation;
import com.rabbit.aip.organisation.OrganisationRepository;
import com.rabbit.aip.user.AccountStatus;
import com.rabbit.aip.user.OrganisationMembership;
import com.rabbit.aip.user.OrganisationMembershipRepository;
import com.rabbit.aip.user.UserAccount;
import com.rabbit.aip.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final OrganisationMembershipRepository memberships;
    private final OrganisationRepository organisations;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTtlDays;

    public AuthService(
            UserAccountRepository users,
            OrganisationMembershipRepository memberships,
            OrganisationRepository organisations,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${rabbit.jwt.refresh-ttl-days}") long refreshTtlDays
    ) {
        this.users = users;
        this.memberships = memberships;
        this.organisations = organisations;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        UserAccount user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(this::invalidCredentials);
        if (user.getStatus() != AccountStatus.ACTIVE || user.isLocked()) {
            throw new DomainException(
                    "ACCOUNT_UNAVAILABLE",
                    "Your account is inactive or locked. Contact your administrator.",
                    HttpStatus.LOCKED
            );
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailedAttempt();
            throw invalidCredentials();
        }
        user.clearFailedAttempts();

        List<OrganisationMembership> active = memberships
                .findAllByUserIdAndStatus(user.getId(), AccountStatus.ACTIVE);
        if (active.isEmpty()) {
            throw new DomainException(
                    "NO_ACTIVE_ORGANISATION",
                    "No active organisation is assigned to this account.",
                    HttpStatus.FORBIDDEN
            );
        }
        List<OrganisationChoice> choices = choices(active);
        if (active.size() > 1) {
            return AuthResponse.selection(
                    jwtService.issueSelectionToken(user, active),
                    choices
            );
        }
        return session(user, active.get(0), choices);
    }

    @Transactional
    public AuthResponse selectOrganisation(String selectionToken, UUID organisationId) {
        Jwt jwt;
        try {
            jwt = jwtService.decodeSelectionToken(selectionToken);
        } catch (RuntimeException exception) {
            throw new DomainException(
                    "SELECTION_TOKEN_INVALID",
                    "Your organisation selection has expired. Please sign in again.",
                    HttpStatus.UNAUTHORIZED
            );
        }
        List<String> allowed = jwt.getClaimAsStringList("organisation_ids");
        if (allowed == null || !allowed.contains(organisationId.toString())) {
            throw DomainException.forbidden(
                    "ORGANISATION_ACCESS_DENIED",
                    "This organisation is not assigned to your account."
            );
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        UserAccount user = users.findById(userId)
                .orElseThrow(this::invalidCredentials);
        OrganisationMembership membership = memberships
                .findByUserIdAndOrganisationIdAndStatus(
                        userId,
                        organisationId,
                        AccountStatus.ACTIVE
                )
                .orElseThrow(() -> DomainException.forbidden(
                        "ORGANISATION_ACCESS_DENIED",
                        "This organisation is not assigned to your account."
                ));
        return session(
                user,
                membership,
                choices(memberships.findAllByUserIdAndStatus(userId, AccountStatus.ACTIVE))
        );
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(this::invalidRefreshToken);
        if (!stored.isUsable()) throw invalidRefreshToken();
        stored.revoke();
        UserAccount user = users.findById(stored.getUserId())
                .orElseThrow(this::invalidRefreshToken);
        OrganisationMembership membership = memberships.findById(stored.getMembershipId())
                .filter(item -> item.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(this::invalidRefreshToken);
        return session(
                user,
                membership,
                choices(memberships.findAllByUserIdAndStatus(
                        user.getId(),
                        AccountStatus.ACTIVE
                ))
        );
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    private AuthResponse session(
            UserAccount user,
            OrganisationMembership membership,
            List<OrganisationChoice> choices
    ) {
        String rawRefresh = UUID.randomUUID() + "." + UUID.randomUUID();
        refreshTokens.save(new RefreshToken(
                hash(rawRefresh),
                user.getId(),
                membership.getOrganisationId(),
                membership.getId(),
                Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS)
        ));
        return AuthResponse.session(
                choices,
                jwtService.issueAccessToken(user, membership),
                rawRefresh,
                jwtService.accessTtlSeconds(),
                membership.getRole(),
                user.isFirstLogin()
        );
    }

    private List<OrganisationChoice> choices(List<OrganisationMembership> active) {
        return active.stream()
                .map(membership -> {
                    Organisation organisation = organisations
                            .findById(membership.getOrganisationId())
                            .orElseThrow(() -> DomainException.notFound(
                                    "ORGANISATION_NOT_FOUND",
                                    "An assigned organisation no longer exists."
                            ));
                    return new OrganisationChoice(
                            organisation.getId(),
                            organisation.getCode(),
                            organisation.getName(),
                            membership.getRole()
                    );
                })
                .toList();
    }

    private DomainException invalidCredentials() {
        return new DomainException(
                "INVALID_CREDENTIALS",
                "Invalid email or password. Please try again.",
                HttpStatus.UNAUTHORIZED
        );
    }

    private DomainException invalidRefreshToken() {
        return new DomainException(
                "REFRESH_TOKEN_INVALID",
                "Your session has expired. Please sign in again.",
                HttpStatus.UNAUTHORIZED
        );
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
}
