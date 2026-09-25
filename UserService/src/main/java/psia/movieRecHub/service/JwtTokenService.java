package psia.movieRecHub.service;

import com.nimbusds.jose.jwk.RSAKey;
import java.net.URI;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import psia.movieRecHub.dto.LoginResponse;
import psia.movieRecHub.model.User;

@Service
public class JwtTokenService {

  private final JwtEncoder encoder;
  private final String keyId;
  private final String issuer;
  private final long ttlSeconds;

  public JwtTokenService(
      JwtEncoder encoder,
      RSAKey signingKey,
      @Value("${auth.jwt.issuer}") String issuer,
      @Value("${auth.jwt.access-token-ttl-seconds}") long ttlSeconds) {
    if (issuer == null || issuer.isBlank() || ttlSeconds < 1 || ttlSeconds > 86400) {
      throw new IllegalArgumentException("Invalid JWT issuer or lifetime");
    }
    URI issuerUri = URI.create(issuer);
    if (!issuerUri.isAbsolute()
        || !("http".equals(issuerUri.getScheme()) || "https".equals(issuerUri.getScheme()))) {
      throw new IllegalArgumentException("JWT issuer must be an absolute HTTP(S) URL");
    }
    this.encoder = encoder;
    this.keyId = signingKey.getKeyID();
    this.issuer = issuer;
    this.ttlSeconds = ttlSeconds;
  }

  public LoginResponse issueFor(User user) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ttlSeconds))
            .claim("username", user.getUsername())
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new LoginResponse(token, "Bearer", ttlSeconds, user.getId(), user.getUsername());
  }
}
