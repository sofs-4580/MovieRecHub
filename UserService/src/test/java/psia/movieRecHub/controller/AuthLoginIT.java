package psia.movieRecHub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import psia.movieRecHub.dto.ApiErrorResponse;
import psia.movieRecHub.dto.LoginResponse;
import psia.movieRecHub.dto.RegisterResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthLoginIT {

  @LocalServerPort private int port;
  @Autowired private TestRestTemplate client;
  @Autowired private JwtDecoder jwtDecoder;
  @Autowired private JwtEncoder jwtEncoder;

  @Test
  void correctCredentialsReturnVerifiableShortLivedJwt() {
    String username = uniqueUsername();
    String password = "senha-longa-ç-123";
    RegisterResponse account = register(username, password);

    ResponseEntity<LoginResponse> response =
        client.postForEntity(
            "/api/auth/login",
            Map.of("username", "  " + username.toUpperCase() + "  ", "password", password),
            LoginResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    LoginResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.tokenType()).isEqualTo("Bearer");
    assertThat(body.expiresIn()).isEqualTo(3600);
    assertThat(body.userId()).isEqualTo(account.userId());
    assertThat(body.username()).isEqualTo(username);
    assertThat(body.accessToken().split("\\.")).hasSize(3);
    Jwt jwt = jwtDecoder.decode(body.accessToken());
    assertThat(jwt.getSubject()).isEqualTo(account.userId().toString());
    assertThat(jwt.getClaimAsString("username")).isEqualTo(username);
    assertThat(jwt.getIssuer().toString()).isEqualTo("http://user-service");
    assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
    assertThat(jwt.getHeaders().get("kid")).isNotNull();
    assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(body.expiresIn()));
  }

  @Test
  void wrongPasswordAndUnknownUsernameHaveIdenticalUnauthorizedResponse() {
    String username = uniqueUsername();
    register(username, "senha-correta-123");

    ResponseEntity<ApiErrorResponse> wrongPassword =
        client.postForEntity(
            "/api/auth/login",
            Map.of("username", username, "password", "senha-errada-123"),
            ApiErrorResponse.class);
    ResponseEntity<ApiErrorResponse> unknownUser =
        client.postForEntity(
            "/api/auth/login",
            Map.of("username", uniqueUsername(), "password", "senha-errada-123"),
            ApiErrorResponse.class);

    assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(wrongPassword.getBody()).isEqualTo(unknownUser.getBody());
    assertThat(wrongPassword.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  void invalidRequestReturnsBadRequestWithoutEchoingPassword() {
    ResponseEntity<ApiErrorResponse> response =
        client.postForEntity(
            "/api/auth/login",
            Map.of("username", "invalid-name", "password", "sensitive-password"),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getBody().message()).doesNotContain("sensitive-password");
  }

  @Test
  void decoderRejectsTamperedAndExpiredTokens() {
    String username = uniqueUsername();
    register(username, "senha-correta-123");
    LoginResponse login =
        client.postForEntity(
                "/api/auth/login",
                Map.of("username", username, "password", "senha-correta-123"),
                LoginResponse.class)
            .getBody();
    String[] parts = login.accessToken().split("\\.");
    String signature = parts[2];
    String tampered =
        parts[0] + "." + parts[1] + "." + (signature.charAt(0) == 'A' ? 'B' : 'A')
            + signature.substring(1);
    assertThatThrownBy(() -> jwtDecoder.decode(tampered)).isInstanceOf(JwtException.class);

    Instant past = Instant.now().minusSeconds(7200);
    JwtClaimsSet expiredClaims =
        JwtClaimsSet.builder()
            .issuer("http://user-service")
            .subject("1")
            .issuedAt(past)
            .expiresAt(past.plusSeconds(60))
            .build();
    String expired = jwtEncoder.encode(JwtEncoderParameters.from(expiredClaims)).getTokenValue();
    assertThatThrownBy(() -> jwtDecoder.decode(expired)).isInstanceOf(JwtException.class);
  }

  @Test
  void publishedJwksContainsOnlyThePublicKeyAndVerifiesIssuedToken() {
    String username = uniqueUsername();
    RegisterResponse account = register(username, "senha-correta-123");
    LoginResponse login =
        client.postForEntity(
                "/api/auth/login",
                Map.of("username", username, "password", "senha-correta-123"),
                LoginResponse.class)
            .getBody();

    ResponseEntity<JsonNode> response =
        client.getForEntity("/.well-known/jwks.json", JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode keys = response.getBody().path("keys");
    assertThat(keys.isArray()).isTrue();
    assertThat(keys.size()).isEqualTo(1);
    JsonNode key = keys.get(0);
    assertThat(key.path("kty").asText()).isEqualTo("RSA");
    assertThat(key.path("use").asText()).isEqualTo("sig");
    assertThat(key.path("alg").asText()).isEqualTo("RS256");
    assertThat(key.path("kid").asText()).isEqualTo(jwtDecoder.decode(login.accessToken()).getHeaders().get("kid"));
    assertThat(key.path("n").asText()).isNotBlank();
    assertThat(key.path("e").asText()).isNotBlank();
    for (String privateField : new String[] {"d", "p", "q", "dp", "dq", "qi", "oth"}) {
      assertThat(key.has(privateField)).isFalse();
    }

    NimbusJwtDecoder remoteDecoder =
        NimbusJwtDecoder.withJwkSetUri("http://localhost:" + port + "/.well-known/jwks.json")
            .build();
    remoteDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("http://user-service"));
    assertThat(remoteDecoder.decode(login.accessToken()).getSubject())
        .isEqualTo(account.userId().toString());
  }

  private RegisterResponse register(String username, String password) {
    ResponseEntity<RegisterResponse> response =
        client.postForEntity(
            "/api/auth/register",
            Map.of("username", username, "password", password),
            RegisterResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody();
  }

  private static String uniqueUsername() {
    return "login_" + UUID.randomUUID().toString().substring(0, 8);
  }
}
