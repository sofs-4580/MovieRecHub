package psia.movieRecHub.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class AuthDtoJsonTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void deserializesCredentialsAndNormalizesOnlyUsername() throws Exception {
    String json = """
        {"username":"  Ana_34  ","password":"  SeGrEdO-ç-123  "}
        """;

    RegisterRequest register = mapper.readValue(json, RegisterRequest.class);
    LoginRequest login = mapper.readValue(json, LoginRequest.class);

    assertThat(register.username()).isEqualTo("ana_34");
    assertThat(login.username()).isEqualTo("ana_34");
    assertThat(register.password()).isEqualTo("  SeGrEdO-ç-123  ");
    assertThat(login.password()).isEqualTo("  SeGrEdO-ç-123  ");
  }

  @Test
  void omittedAndNullPasswordsRemainNullForBeanValidation() throws Exception {
    for (String json :
        new String[] {"{\"username\":\"ana_34\"}", "{\"username\":\"ana_34\",\"password\":null}"}) {
      assertThat(mapper.readValue(json, RegisterRequest.class).password()).isNull();
      assertThat(mapper.readValue(json, LoginRequest.class).password()).isNull();
    }
  }

  @Test
  void passwordsAreNotSerializedBackToJson() throws Exception {
    JsonNode expected = mapper.readTree("{\"username\":\"ana_34\"}");
    JsonNode register = mapper.valueToTree(new RegisterRequest("ana_34", "private-password"));
    JsonNode login = mapper.valueToTree(new LoginRequest("ana_34", "private-password"));

    assertThat(register).isEqualTo(expected);
    assertThat(login).isEqualTo(expected);
  }

  @Test
  void registrationResponseContainsOnlyPublicAccountIdentifiers() throws Exception {
    JsonNode actual = mapper.readTree(mapper.writeValueAsString(new RegisterResponse(1L, "ana_34")));

    assertThat(actual).isEqualTo(mapper.readTree("{\"userId\":1,\"username\":\"ana_34\"}"));
  }

  @Test
  void loginResponseContainsTokenMetadataWithoutPasswordOrHash() throws Exception {
    JsonNode actual =
        mapper.readTree(
            mapper.writeValueAsString(
                new LoginResponse("example-token", "Bearer", 900, 1L, "ana_34")));

    assertThat(actual)
        .isEqualTo(
            mapper.readTree(
                """
                {
                  "accessToken":"example-token",
                  "tokenType":"Bearer",
                  "expiresIn":900,
                  "userId":1,
                  "username":"ana_34"
                }
                """));
  }

  @Test
  void stringRepresentationsDoNotExposeCredentials() {
    assertThat(new RegisterRequest("ana_34", "private-password").toString())
        .doesNotContain("private-password");
    assertThat(new LoginRequest("ana_34", "private-password").toString())
        .doesNotContain("private-password");
    assertThat(new LoginResponse("private-token", "Bearer", 900, 1L, "ana_34").toString())
        .doesNotContain("private-token");
  }
}
