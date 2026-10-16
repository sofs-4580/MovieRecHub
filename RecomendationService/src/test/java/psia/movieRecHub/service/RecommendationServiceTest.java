package psia.movieRecHub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import psia.movieRecHub.client.OllamaLlmClient;
import psia.movieRecHub.dto.response.RecommendationResponseItemDto;
import psia.movieRecHub.dto.response.RecommendationResultDto;
import psia.movieRecHub.engine.Hash;
import psia.movieRecHub.engine.MovieDoc;
import psia.movieRecHub.engine.UserTaste;
import psia.movieRecHub.exceptions.LlmUnavailableException;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  @Mock CatalogRefreshService catalogue;
  @Mock OllamaLlmClient llm;
  @Mock RecommendationCacheService cacheService;

  RecommendationService service;

  private static MovieDoc movie(long id, String title, int year, double rating) {
    return new MovieDoc(id, title, year, Set.of("DRAMA"), "", rating, "", "");
  }

  private static List<Long> ids(RecommendationResultDto result) {
    return result.items().stream().map(RecommendationResponseItemDto::movieId).toList();
  }

  @BeforeEach
  void setUp() {
    service = new RecommendationService(catalogue, llm, cacheService);
  }

  private void catalogueOfSix() {
    when(catalogue.movies())
        .thenReturn(
            List.of(
                movie(1, "A", 1990, 7.0),
                movie(2, "B", 2000, 9.0),
                movie(3, "C", 2010, 8.0),
                movie(4, "D", 2020, 9.0),
                movie(5, "E", 1980, 6.0),
                movie(6, "F", 1970, 5.0)));
  }

  @Test
  void askReturnsTheMoviesTheLlmChoseInThatOrder() {
    catalogueOfSix();
    when(llm.generateJson(anyString(), anyString(), anyMap()))
        .thenReturn("{\"count\":2,\"ids\":[3,1,2,4,5]}");

    RecommendationResultDto result = service.ask("two movies", new UserTaste("", Set.of()));

    assertEquals("LLM", result.source());
    assertEquals(List.of(3L, 1L), ids(result));
  }

  @Test
  void askIgnoresIdsThatAreNotInTheCatalogue() {
    catalogueOfSix();
    when(llm.generateJson(anyString(), anyString(), anyMap()))
        .thenReturn("{\"count\":5,\"ids\":[99,2,-1,2,3]}");

    RecommendationResultDto result = service.ask("a movie", new UserTaste("", Set.of()));

    assertEquals("LLM", result.source());
    assertEquals(List.of(2L, 3L), ids(result));
  }

  @Test
  void askSendsTheSeenIdsAndTheTasteToTheModel() {
    catalogueOfSix();
    when(llm.generateJson(anyString(), anyString(), anyMap())).thenReturn("{\"ids\":[1]}");

    service.ask("a comedy", new UserTaste("Favorites: Heat.", Set.of(8L, 7L)));

    ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
    verify(llm).generateJson(anyString(), user.capture(), anyMap());
    assertTrue(user.getValue().contains("Already seen (do NOT choose these ids): 7, 8"));
    assertTrue(user.getValue().contains("Favorites: Heat."));
    assertTrue(user.getValue().contains("Request: \"a comedy\""));
  }

  @Test
  void fallsBackToBestRatedThenNewestWhenTheLlmIsDown() {
    catalogueOfSix();
    when(llm.generateJson(anyString(), anyString(), anyMap()))
        .thenThrow(new LlmUnavailableException("down"));

    RecommendationResultDto result = service.ask("a movie", new UserTaste("", Set.of()));

    assertEquals("FALLBACK", result.source());
    // nota 9.0: D (2020) antes de B (2000); depois C (8.0), A (7.0), E (6.0)
    assertEquals(List.of(4L, 2L, 3L, 1L, 5L), ids(result));
  }

  @Test
  void fallsBackWhenTheLlmAnswerHasNoValidIds() {
    catalogueOfSix();
    when(llm.generateJson(anyString(), anyString(), anyMap())).thenReturn("not json");

    RecommendationResultDto result = service.ask("a movie", new UserTaste("", Set.of()));

    assertEquals("FALLBACK", result.source());
    assertEquals(5, result.items().size());
  }

  @Test
  void emptyCatalogueGivesEmptyFallbackWithoutCallingTheModel() {
    when(catalogue.movies()).thenReturn(List.of());

    RecommendationResultDto result = service.ask("a movie", new UserTaste("", Set.of()));

    assertEquals("FALLBACK", result.source());
    assertTrue(result.items().isEmpty());
    verifyNoInteractions(llm);
  }

  @Test
  void refreshForCachesLlmResultsWithTheHashOfTheTaste() {
    catalogueOfSix();
    when(llm.generateJson(anyString(), anyString(), anyMap())).thenReturn("{\"ids\":[1,2,3,4,5]}");

    RecommendationResultDto result = service.refreshFor(5L, "Favorites: Heat.", Set.of(6L));

    assertEquals("LLM", result.source());
    verify(cacheService)
        .saveRecommendationCache(eq(5L), eq(result), eq(Hash.sha256("Favorites: Heat.")));
  }

  @Test
  void refreshForNeverCachesAFallback() {
    catalogueOfSix();
    when(llm.generateJson(anyString(), anyString(), anyMap()))
        .thenThrow(new LlmUnavailableException("down"));

    RecommendationResultDto result = service.refreshFor(5L, "Favorites: Heat.", Set.of());

    assertEquals("FALLBACK", result.source());
    verify(cacheService, never()).saveRecommendationCache(any(), any(), any());
  }
}
