package psia.movieRecHub.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChangePasswordRequestTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void close() {
    factory.close();
  }

  @Test
  void acceptsNewPasswordBoundariesAndDoesNotReapplyMinimumToCurrentPassword() {
    for (int length : new int[] {12, 128}) {
      assertThat(validator.validate(new ChangePasswordRequest("x", "ç".repeat(length)))).isEmpty();
    }
  }

  @Test
  void rejectsMissingBlankOrOversizedCurrentPassword() {
    for (String current : Arrays.asList(null, "", "  ", "x".repeat(129))) {
      assertThat(validator.validate(new ChangePasswordRequest(current, "valid-new-password")))
          .isNotEmpty();
    }
  }

  @Test
  void rejectsInvalidNewPassword() {
    for (String next : Arrays.asList(null, "", " ".repeat(12), "x".repeat(11), "x".repeat(129))) {
      assertThat(validator.validate(new ChangePasswordRequest("current-password", next))).isNotEmpty();
    }
  }

  @Test
  void preservesPasswordExactlyButNeverSerializesOrPrintsSecrets() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String json = "{\"currentPassword\":\"  Old-ç-123456  \",\"newPassword\":\"  New-ç-123456  \"}";
    ChangePasswordRequest request = mapper.readValue(json, ChangePasswordRequest.class);
    assertThat(request.currentPassword()).isEqualTo("  Old-ç-123456  ");
    assertThat(request.newPassword()).isEqualTo("  New-ç-123456  ");
    assertThat(mapper.writeValueAsString(request)).isEqualTo("{}");
    assertThat(request.toString()).doesNotContain("Old-ç-123456", "New-ç-123456");
  }
}
