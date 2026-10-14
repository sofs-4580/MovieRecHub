package psia.movieRecHub.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import psia.movieRecHub.dto.request.AskRequestDto;
import psia.movieRecHub.dto.response.RecommendationResultDto;
import psia.movieRecHub.service.RecommendationCacheService;
import psia.movieRecHub.service.RecommendationService;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

  private final RecommendationService service;
  private final RecommendationCacheService cacheService;

  public RecommendationController(
      RecommendationService service, RecommendationCacheService cacheService) {
    this.service = service;
    this.cacheService = cacheService;
  }

  @PostMapping
  public RecommendationResultDto ask(
      @RequestHeader(value = "X-User-Id", required = false) Long userId,
      @RequestBody AskRequestDto request) {
    if (request == null || request.text() == null || request.text().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
    }
    return service.ask(request.text());
  }

  @GetMapping
  public RecommendationResultDto getCache(@RequestHeader("X-User-Id") Long userId) {
    return cacheService.find(userId).orElse(new RecommendationResultDto("PENDING", List.of()));
  }

  /** Provisório, para testar a cache à mão. O agente vai substituir isto. */
  @PostMapping("/users/{userId}/refresh")
  public RecommendationResultDto refresh(
      @PathVariable Long userId, @RequestBody AskRequestDto request) {
    return service.refreshFor(userId, request.text());
  }
}
