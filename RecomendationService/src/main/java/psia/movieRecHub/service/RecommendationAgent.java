package psia.movieRecHub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import psia.movieRecHub.engine.Hash;
import psia.movieRecHub.engine.UserTaste;
import psia.movieRecHub.entities.KnownUser;
import psia.movieRecHub.repository.KnownUserRepository;

// Prepara em segundo plano as recomendacoes do login, para o GET só ter de ler a cache
@Component
public class RecommendationAgent {

  private static final Logger log = LoggerFactory.getLogger(RecommendationAgent.class);

  private final KnownUserRepository users;
  private final UserTasteService tasteService;
  private final RecommendationService recommendationService;
  private final RecommendationCacheService cacheService;
  private final int maxPerRun;

  public RecommendationAgent(
      KnownUserRepository users,
      UserTasteService tasteService,
      RecommendationService recommendationService,
      RecommendationCacheService cacheService,
      @Value("${recommendation.agent.max-per-run:3}") int maxPerRun) {
    this.users = users;
    this.tasteService = tasteService;
    this.recommendationService = recommendationService;
    this.cacheService = cacheService;
    this.maxPerRun = maxPerRun;
  }

  @Scheduled(
      initialDelayString = "${recommendation.agent.initial-delay-ms:30000}",
      fixedDelayString = "${recommendation.agent.interval-ms:300000}")
  public void run() {
    int generated = 0;
    for (KnownUser user : users.findAll()) {
      if (generated >= maxPerRun) {
        break; // o resto fica para a prox ronda
      }
      if (processUser(user.getUserId())) {
        generated++;
      }
    }
    log.info("Agent run finished: {} recommendation(s) generated", generated);
  }

  // devolve true se chamou a ai para este utilizador
  private boolean processUser(Long userId) {
    try {
      UserTaste taste = tasteService.load(userId);
      if (taste.tasteText().isBlank()) {
        return false; // sem listas, reviews nem perfil ->  nada para recomendar
      }
      String hash = Hash.sha256(taste.tasteText());
      if (hash.equals(cacheService.findInputHash(userId).orElse(null))) {
        return false; // o gosto n mudou
      }
      log.info("Agent: generating recommendations for user {}", userId);
      recommendationService.refreshFor(userId, taste.tasteText());
      return true;
    } catch (RuntimeException e) {
      log.warn("Agent: skipping user {}: {}", userId, e.getMessage());
      return false; // um utilizador com erro n para os outros
    }
  }
}
