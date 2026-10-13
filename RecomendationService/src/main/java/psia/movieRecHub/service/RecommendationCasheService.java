package psia.movieRecHub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.dto.response.RecommendationResponseItemDto;
import psia.movieRecHub.dto.response.RecommendationResultDto;
import psia.movieRecHub.entities.RecommendationCache;
import psia.movieRecHub.repository.RecommendationCacheRepository;

public class RecommendationCasheService {

  private final RecommendationCacheRepository repo;
  private final ObjectMapper objectMapper;
  private final String model;

  public RecommendationCasheService(
      RecommendationCacheRepository repo,
      ObjectMapper objectMapper,
      @Value("${llm.model}") String model) {
    this.repo = repo;
    this.objectMapper = objectMapper;
    this.model = model;
  }

  public void saveRecommendationCache(
      Long userId, RecommendationResultDto result, String inputHash) {
    RecommendationCache row =
        repo.findById(userId).orElseGet(() -> new RecommendationCache(userId));
    row.setItems(objectMapper.valueToTree(result.items()));
    row.setSource(result.source());
    row.setModel("LLM".equals(result.source()) ? model : null);
    row.setInputHash(inputHash);
    row.setGeneratedAt(Instant.now());
    repo.save(row);
  }

  @Transactional(readOnly = true)
  public Optional<RecommendationResultDto> find(Long userId) {
    return repo.findById(userId)
        .map(
            row ->
                new RecommendationResultDto(
                    row.getSource(),
                    objectMapper.convertValue(
                        row.getItems(),
                        new TypeReference<List<RecommendationResponseItemDto>>() {})));
  }
}
