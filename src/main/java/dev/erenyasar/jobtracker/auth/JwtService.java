package dev.erenyasar.jobtracker.auth;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived HS256 access tokens whose subject is the user id. */
@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder, @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        this.encoder = encoder;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("job-tracker-api")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("email", user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long ttlSeconds() { return ttl.toSeconds(); }
}
