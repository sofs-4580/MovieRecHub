package psia.movieRecHub.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import psia.movieRecHub.dto.request.AskRequestDto;
import psia.movieRecHub.dto.request.ProfileRequestDto;
import psia.movieRecHub.dto.response.RecommendationResultDto;
import psia.movieRecHub.engine.Hash;
import psia.movieRecHub.engine.UserTaste;
import psia.movieRecHub.service.KnowUserService;
import psia.movieRecHub.service.RecommendationCacheService;
import psia.movieRecHub.service.RecommendationService;
import psia.movieRecHub.service.UserTasteService;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

  private final RecommendationService service;
  private final RecommendationCacheService cacheService;
  private final UserTasteService tasteService;
  private final KnowUserService knownUserService;

  public RecommendationController(
      RecommendationService service,
      RecommendationCacheService cacheService,
      UserTasteService tasteService,
      KnowUserService knownUserService) {
    this.service = service;
    this.cacheService = cacheService;
    this.tasteService = tasteService;
    this.knownUserService = knownUserService;
  }

  @PostMapping
  public RecommendationResultDto ask(
      @RequestHeader(value = "X-User-Id", required = false) Long userId,
      @RequestBody AskRequestDto request) {
    if (request == null || request.text() == null || request.text().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
    }
    UserTaste taste = new UserTaste("", Set.of());
    if (userId != null) {
      try {
        taste = tasteService.load(userId);
      } catch (RuntimeException e) {
        // o pedido escrito tem de funcionar mesmo sem o gosto
      }
    }
    return service.ask(request.text(), taste);
  }

  @GetMapping
  public RecommendationResultDto getCache(@RequestHeader("X-User-Id") Long userId) {
    knownUserService.register(userId);
    return cacheService.find(userId).orElse(new RecommendationResultDto("PENDING", List.of()));
  }

  // novo campo + construtor: private final UserSignalsService signalsService;

  @GetMapping("/debug/signals/{userId}")
  public Map<String, Object> debugSignals(@PathVariable Long userId) {
    UserTaste s = tasteService.load(userId);
    return Map.of(
        "taste", s.tasteText(),
        "hash", Hash.sha256(s.tasteText()),
        "knownMovieIds", s.knownMovieIds());
  }

  @PutMapping("/users/{userId}/profile")
  public ResponseEntity<Void> saveProfile(
      @PathVariable Long userId, @RequestBody ProfileRequestDto body) {
    knownUserService.saveProfile(userId, body.favoriteGenres(), body.favoriteDecades());
    return ResponseEntity.noContent().build();
  }
}
