package psia.movieRecHub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigTest {

  @Test
  void hashesFullLengthPasswordsWithoutTruncatingTheirSuffix() {
    PasswordEncoder encoder = new PasswordConfig().passwordEncoder();
    String password = "ç".repeat(127) + "x";
    String changedSuffix = "ç".repeat(127) + "y";

    String hash = encoder.encode(password);

    assertThat(hash).startsWith("{pbkdf2@SpringSecurity_v5_8}");
    assertThat(hash).isNotEqualTo(password).hasSizeLessThanOrEqualTo(255);
    assertThat(encoder.matches(password, hash)).isTrue();
    assertThat(encoder.matches(changedSuffix, hash)).isFalse();
    assertThat(encoder.encode(password)).isNotEqualTo(hash);
  }
}
