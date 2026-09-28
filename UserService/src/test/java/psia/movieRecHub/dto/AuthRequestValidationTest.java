package psia.movieRecHub.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthRequestValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ana_34", "ANA_34", "  Ana_34  "})
  void acceptsAndNormalizesUsernameForBothRequests(String username) {
    RegisterRequest register = registration(username, "valid-password");
    LoginRequest login = new LoginRequest(username, "valid-password");

    assertThat(validator.validate(register)).isEmpty();
    assertThat(validator.validate(login)).isEmpty();
    assertThat(register.username()).isEqualTo("ana_34");
    assertThat(login.username()).isEqualTo("ana_34");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "ab", "ana maria", "ana@34", "ana-34", "aná_34"})
  void rejectsInvalidUsernameForBothRequests(String username) {
    assertInvalidField(registration(username, "valid-password"), "username");
    assertInvalidField(new LoginRequest(username, "valid-password"), "username");
  }

  @ParameterizedTest
  @ValueSource(ints = {3, 30})
  void acceptsUsernameLengthBoundaries(int length) {
    String username = "a".repeat(length);

    assertThat(validator.validate(registration(username, "valid-password"))).isEmpty();
    assertThat(validator.validate(new LoginRequest(username, "valid-password"))).isEmpty();
  }

  @Test
  void rejectsUsernameLongerThanDatabaseColumn() {
    String username = "a".repeat(31);

    assertInvalidField(registration(username, "valid-password"), "username");
    assertInvalidField(new LoginRequest(username, "valid-password"), "username");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "            ", "\t\n"})
  void rejectsMissingOrBlankPasswordsForBothRequests(String password) {
    assertInvalidField(registration("ana_34", password), "password");
    assertInvalidField(new LoginRequest("ana_34", password), "password");
  }

  @ParameterizedTest
  @ValueSource(ints = {12, 128})
  void acceptsRegistrationPasswordLengthBoundaries(int length) {
    assertThat(validator.validate(registration("ana_34", "x".repeat(length)))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 11, 129})
  void rejectsRegistrationPasswordOutsideAllowedLength(int length) {
    assertInvalidField(registration("ana_34", "x".repeat(length)), "password");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 11, 128})
  void loginDoesNotReapplyRegistrationMinimumLength(int length) {
    assertThat(validator.validate(new LoginRequest("ana_34", "x".repeat(length)))).isEmpty();
  }

  @Test
  void rejectsOversizedLoginPassword() {
    assertInvalidField(new LoginRequest("ana_34", "x".repeat(129)), "password");
  }

  @Test
  void preservesPasswordCaseWhitespaceAndUnicode() {
    String password = "  SeGrEdO-ç-123  ";
    RegisterRequest register = registration("ana_34", password);
    LoginRequest login = new LoginRequest("ana_34", password);

    assertThat(register.password()).isEqualTo(password);
    assertThat(login.password()).isEqualTo(password);
    assertThat(validator.validate(register)).isEmpty();
    assertThat(validator.validate(login)).isEmpty();
  }

  @Test
  void rejectsMissingEmptyAndInvalidFavoriteGenres() {
    assertInvalidField(new RegisterRequest("ana_34", "valid-password", null), "favoriteGenres");
    assertInvalidField(new RegisterRequest("ana_34", "valid-password", Set.of()), "favoriteGenres");
    for (String invalid : new String[] {"WESTERN", "drama", " "}) {
      assertThat(validator.validate(new RegisterRequest("ana_34", "valid-password", Set.of(invalid))))
          .isNotEmpty();
    }
    assertThat(validator.validate(registration("ana_34", "valid-password"))).isEmpty();
  }

  private static RegisterRequest registration(String username, String password) {
    return new RegisterRequest(username, password, Set.of("DRAMA"));
  }

  private static void assertInvalidField(Object request, String field) {
    assertThat(validator.validate(request))
        .isNotEmpty()
        .allSatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo(field));
  }
}
