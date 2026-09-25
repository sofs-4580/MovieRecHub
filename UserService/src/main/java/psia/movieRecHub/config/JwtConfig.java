package psia.movieRecHub.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

  @Bean
  public RSAKey signingKey(
      @Value("${auth.jwt.private-key-location}") Resource privateKeyResource,
      @Value("${auth.jwt.public-key-location}") Resource publicKeyResource) {
    try {
      KeyFactory factory = KeyFactory.getInstance("RSA");
      RSAPrivateKey privateKey =
          (RSAPrivateKey)
              factory.generatePrivate(
                  new PKCS8EncodedKeySpec(readPem(privateKeyResource, "PRIVATE KEY")));
      RSAPublicKey publicKey =
          (RSAPublicKey)
              factory.generatePublic(new X509EncodedKeySpec(readPem(publicKeyResource, "PUBLIC KEY")));
      if (publicKey.getModulus().bitLength() < 2048
          || !privateKey.getModulus().equals(publicKey.getModulus())) {
        throw new IllegalArgumentException("Invalid RSA signing key pair");
      }
      RSAKey publicJwk = new RSAKey.Builder(publicKey).build();
      return new RSAKey.Builder(publicKey)
          .privateKey(privateKey)
          .keyUse(KeyUse.SIGNATURE)
          .algorithm(JWSAlgorithm.RS256)
          .keyID(publicJwk.computeThumbprint().toString())
          .build();
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot load JWT signing key pair", exception);
    }
  }

  @Bean
  public JwtEncoder jwtEncoder(RSAKey signingKey) {
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
  }

  @Bean
  public JwtDecoder jwtDecoder(RSAKey signingKey, @Value("${auth.jwt.issuer}") String issuer)
      throws JOSEException {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }

  private static byte[] readPem(Resource resource, String type) throws IOException {
    String pem;
    try (InputStream stream = resource.getInputStream()) {
      pem = new String(stream.readAllBytes(), StandardCharsets.US_ASCII);
    }
    String begin = "-----BEGIN " + type + "-----";
    String end = "-----END " + type + "-----";
    if (!pem.contains(begin) || !pem.contains(end)) {
      throw new IllegalArgumentException("Expected PEM " + type);
    }
    String encoded = pem.substring(pem.indexOf(begin) + begin.length(), pem.indexOf(end));
    return Base64.getMimeDecoder().decode(encoded);
  }
}
