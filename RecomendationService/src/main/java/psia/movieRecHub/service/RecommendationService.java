package psia.movieRecHub.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import psia.movieRecHub.client.OllamaLlmClient;
import psia.movieRecHub.dto.response.RecommendationResponseItemDto;
import psia.movieRecHub.dto.response.RecommendationResultDto;
import psia.movieRecHub.engine.Hash;
import psia.movieRecHub.engine.LlmAnswer;
import psia.movieRecHub.engine.LlmPrompts;
import psia.movieRecHub.engine.MovieDoc;
import psia.movieRecHub.engine.UserTaste;
import psia.movieRecHub.exceptions.LlmUnavailableException;

@Service
public class RecommendationService {

  private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
  private static final int LIMIT = 5;

  private final CatalogRefreshService catalogue;
  private final OllamaLlmClient llm;
  private final RecommendationCacheService cacheService;

  public RecommendationService(
      CatalogRefreshService catalogue,
      OllamaLlmClient llm,
      RecommendationCacheService cacheService) {
    this.catalogue = catalogue;
    this.llm = llm;
    this.cacheService = cacheService;
  }

  // prompt texto livre e llm recomenda no momento
  public RecommendationResultDto ask(String text, UserTaste taste) {
    List<MovieDoc> movies = catalogue.movies();
    return recommend(
        movies,
        LlmPrompts.askSystem(),
        LlmPrompts.askUser(text, taste.tasteText(), taste.knownMovieIds(), movies),
        LlmPrompts.ASK_SCHEMA);
  }

  // recomendacoes qundo o user entra na aplicacao
  public RecommendationResultDto refreshFor(Long userId, String tastePrompt, Set<Long> seen) {
    List<MovieDoc> movies = catalogue.movies();
    RecommendationResultDto result =
        recommend(
            movies,
            LlmPrompts.tasteSystem(),
            LlmPrompts.tasteUser(tastePrompt, seen, movies),
            LlmPrompts.TASTE_SCHEMA);
    if ("LLM".equals(result.source())) {
      cacheService.saveRecommendationCache(userId, result, Hash.sha256(tastePrompt));
    }
    return result;
  }

  private RecommendationResultDto recommend(
      List<MovieDoc> movies, String system, String user, Map<String, Object> schema) {
    if (movies.isEmpty()) {
      return new RecommendationResultDto("FALLBACK", List.of());
    }
    Map<Long, MovieDoc> byId =
        movies.stream().collect(Collectors.toMap(MovieDoc::id, Function.identity()));

    try {
      String json = llm.generateJson(system, user, schema);
      log.info("LLM raw answer: {}", json);

      List<Long> ids = LlmAnswer.parseIds(json, byId.keySet(), LIMIT);
      if (!ids.isEmpty()) {
        List<RecommendationResponseItemDto> items =
            ids.stream().map(id -> toItem(byId.get(id))).toList();
        return new RecommendationResultDto("LLM", items);
      }
      log.warn("LLM gave no valid ids, using fallback");
    } catch (LlmUnavailableException e) {
      log.warn("LLM unavailable, using fallback: {}", e.getMessage());
    }
    return fallback(movies);
  }

  private RecommendationResultDto fallback(List<MovieDoc> movies) {
    List<RecommendationResponseItemDto> items =
        movies.stream()
            .sorted(
                Comparator.comparingDouble(MovieDoc::avgRating)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(MovieDoc::year).reversed()))
            .limit(LIMIT)
            .map(this::toItem)
            .toList();
    return new RecommendationResultDto("FALLBACK", items);
  }

  private RecommendationResponseItemDto toItem(MovieDoc m) {
    return new RecommendationResponseItemDto(
        m.id(), m.title(), m.year(), m.genres(), "Genres: " + String.join(", ", m.genres()));
  }
}
