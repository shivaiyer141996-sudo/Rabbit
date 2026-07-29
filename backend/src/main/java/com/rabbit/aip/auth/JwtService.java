package com.rabbit.aip.auth;

import com.rabbit.aip.user.OrganisationMembership;
import com.rabbit.aip.user.UserAccount;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long accessTtlMinutes;

    public JwtService(
            JwtEncoder encoder,
            JwtDecoder decoder,
            @Value("${rabbit.jwt.access-ttl-minutes}") long accessTtlMinutes
    ) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.accessTtlMinutes = accessTtlMinutes;
    }

    public String issueSelectionToken(
            UserAccount user,
            List<OrganisationMembership> memberships
    ) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("rabbit-aip")
                .issuedAt(now)
                .expiresAt(now.plus(5, ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("token_type", "organisation_selection")
                .claim(
                        "organisation_ids",
                        memberships.stream()
                                .map(item -> item.getOrganisationId().toString())
                                .toList()
                )
                .build();
        return encode(claims);
    }

    public String issueAccessToken(
            UserAccount user,
            OrganisationMembership membership
    ) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("rabbit-aip")
                .issuedAt(now)
                .expiresAt(now.plus(accessTtlMinutes, ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("org_id", membership.getOrganisationId().toString())
                .claim("membership_id", membership.getId().toString())
                .claim("role", membership.getRole().name())
                .claim("token_type", "access")
                .build();
        return encode(claims);
    }

    public Jwt decodeSelectionToken(String token) {
        Jwt jwt = decoder.decode(token);
        if (!"organisation_selection".equals(jwt.getClaimAsString("token_type"))) {
            throw new IllegalArgumentException("Invalid organisation selection token.");
        }
        return jwt;
    }

    public long accessTtlSeconds() {
        return accessTtlMinutes * 60;
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}
