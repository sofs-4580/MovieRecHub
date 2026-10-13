package psia.movieRecHub.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import psia.movieRecHub.dto.request.AskRequestDto;
import psia.movieRecHub.dto.response.RecommendationResultDto;
import psia.movieRecHub.service.RecommendationService;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

  private final RecommendationService service;

  public RecommendationController(RecommendationService service) {
    this.service = service;
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
}
