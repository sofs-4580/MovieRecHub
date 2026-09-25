package psia.movieRecHub.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

  private final Map<String, Object> publicJwks;

  public JwksController(RSAKey signingKey) {
    // Build the response from the public part of the key, never from the signing key itself.
    this.publicJwks = new JWKSet(signingKey.toPublicJWK()).toJSONObject(true);
  }

  @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> publicKeys() {
    return publicJwks;
  }
}
