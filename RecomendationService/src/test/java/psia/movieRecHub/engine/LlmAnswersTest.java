package psia.movieRecHub.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmAnswersTest {

  @Test
  void ignoresIdsNotInCatalogue() {
    String json = "{\"picks\":[{\"id\":1,\"reason\":\"ok\"},{\"id\":999,\"reason\":\"invented\"}]}";
    List<LlmAnswer.Pick> picks = LlmAnswer.parsePicks(json, Set.of(1L, 2L), 5);
    assertEquals(1, picks.size());
    assertEquals(1L, picks.get(0).movieId());
  }

  @Test
  void removesDuplicatesAndRespectsLimit() {
    String json =
        "{\"picks\":[{\"id\":1,\"reason\":\"a\"},{\"id\":1,\"reason\":\"b\"},"
            + "{\"id\":2,\"reason\":\"c\"},{\"id\":3,\"reason\":\"d\"}]}";
    List<LlmAnswer.Pick> picks = LlmAnswer.parsePicks(json, Set.of(1L, 2L, 3L), 2);
    assertEquals(2, picks.size());
  }

  @Test
  void invalidJsonGivesEmptyList() {
    assertTrue(LlmAnswer.parsePicks("not json", Set.of(1L), 5).isEmpty());
  }
}
