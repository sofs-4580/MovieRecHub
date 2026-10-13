package psia.movieRecHub.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import psia.movieRecHub.exceptions.LlmUnavailableException;

@Service
public class RecommendationService {

  private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
  private static final int LIMIT = 5;

  private final CatalogRefreshService catalogue;
  private final OllamaLlmClient llm;
  private final RecommendationCasheService cacheService;

  public RecommendationService(
      CatalogRefreshService catalogue,
      OllamaLlmClient llm,
      RecommendationCasheService cacheService) {
    this.catalogue = catalogue;
    this.llm = llm;
    this.cacheService = cacheService;
  }

  public RecommendationResultDto ask(String text) {
    List<MovieDoc> movies = catalogue.movies();
    if (movies.isEmpty()) {
      return new RecommendationResultDto("FALLBACK", List.of());
    }
    Map<Long, MovieDoc> byId =
        movies.stream().collect(Collectors.toMap(MovieDoc::id, Function.identity()));

    try {
      String json =
          llm.generateJson(
              LlmPrompts.pickSystem(),
              LlmPrompts.pickUser(text, movies, LIMIT),
              LlmPrompts.PICK_SCHEMA);

      log.info("LLM raw answer: {}", json);

      List<LlmAnswer.Pick> picks = LlmAnswer.parsePicks(json, byId.keySet(), LIMIT);
      if (!picks.isEmpty()) {
        List<RecommendationResponseItemDto> items =
            picks.stream().map(p -> toItem(byId.get(p.movieId()), p.reason())).toList();
        return new RecommendationResultDto("LLM", items);
      }
      log.warn("LLM gave no valid picks, using fallback");
    } catch (LlmUnavailableException e) {
      log.warn("LLM unavailable, using fallback: {}", e.getMessage());
    }
    return fallback(movies);
  }

  /** Plano B: melhores por rating, depois mais recentes. */
  private RecommendationResultDto fallback(List<MovieDoc> movies) {
    List<RecommendationResponseItemDto> items =
        movies.stream()
            .sorted(
                Comparator.comparingDouble(MovieDoc::avgRating)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(MovieDoc::year).reversed()))
            .limit(LIMIT)
            .map(m -> toItem(m, "Popular in the catalogue"))
            .toList();
    return new RecommendationResultDto("FALLBACK", items);
  }

  private RecommendationResponseItemDto toItem(MovieDoc m, String reason) {
    return new RecommendationResponseItemDto(m.id(), m.title(), m.year(), m.genres(), reason);
  }

  public RecommendationResultDto refreshFor(Long userId, String prompt) {
    RecommendationResultDto result = ask(prompt);
    cacheService.saveRecommendationCache(userId, result, Hash.sha256(prompt));
    return result;
  }
}
