package psia.movieRecHub.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmAnswersTest {

  private static final Set<Long> CATALOGUE = Set.of(1L, 2L, 3L, 4L, 5L, 6L);

  @Test
  void ignoresIdsNotInCatalogue() {
    String json = "{\"count\":5,\"ids\":[1,99,2,100,3]}";
    assertEquals(List.of(1L, 2L, 3L), LlmAnswer.parseIds(json, CATALOGUE, 5));
  }

  @Test
  void removesDuplicates() {
    String json = "{\"count\":5,\"ids\":[1,1,2,2,3]}";
    assertEquals(List.of(1L, 2L, 3L), LlmAnswer.parseIds(json, CATALOGUE, 5));
  }

  @Test
  void invalidJsonGivesEmptyList() {
    assertEquals(List.of(), LlmAnswer.parseIds("not json", CATALOGUE, 5));
  }

  @Test
  void respectsRequestedCount() {
    String json = "{\"count\":2,\"ids\":[1,2,3,4,5]}";
    assertEquals(List.of(1L, 2L), LlmAnswer.parseIds(json, CATALOGUE, 5));
  }

  @Test
  void clampsAbsurdCountAndWorksWithoutCount() {
    assertEquals(
        List.of(1L, 2L, 3L, 4L, 5L),
        LlmAnswer.parseIds("{\"count\":50,\"ids\":[1,2,3,4,5]}", CATALOGUE, 5));
    assertEquals(
        List.of(1L, 2L, 3L, 4L, 5L), LlmAnswer.parseIds("{\"ids\":[1,2,3,4,5]}", CATALOGUE, 5));
  }

  @Test
  void skipsNonNumericIds() {
    String json = "{\"count\":5,\"ids\":[\"abc\",1,null,2]}";
    assertEquals(List.of(1L, 2L), LlmAnswer.parseIds(json, CATALOGUE, 5));
  }

  @Test
  void countBelowOneStillGivesOneMovie() {
    assertEquals(List.of(1L), LlmAnswer.parseIds("{\"count\":0,\"ids\":[1,2,3]}", CATALOGUE, 5));
  }
}
