package psia.movieRecHub.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

  @ParameterizedTest
  @ValueSource(strings = {"ana_34", "ANA_34", "  Ana_34  "})
  void normalizesUsernameWithoutChangingEncodedPassword(String username) {
    User user = new User(username, "EncodedHashForPersistenceTests");

    assertThat(user.getUsername()).isEqualTo("ana_34");
    assertThat(user.getPasswordHash()).isEqualTo("EncodedHashForPersistenceTests");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "ab", "ana maria", "ana@34", "abcdefghijklmnopqrstuvwxyza12345"})
  void rejectsInvalidUsernames(String username) {
    assertThatIllegalArgumentException().isThrownBy(() -> new User(username, "encoded-hash"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void rejectsMissingPasswordHashes(String passwordHash) {
    assertThatIllegalArgumentException().isThrownBy(() -> new User("ana_34", passwordHash));
  }
}
