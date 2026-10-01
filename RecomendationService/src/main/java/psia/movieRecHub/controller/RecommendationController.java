package psia.movieRecHub.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psia.movieRecHub.dto.request.RecommendationRequest;
import psia.movieRecHub.dto.response.RecommendationResponse;
import psia.movieRecHub.service.RecommendationService;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

  private final RecommendationService recommendationService;

  public RecommendationController(RecommendationService recommendationService) {
    this.recommendationService = recommendationService;
  }

  @PostMapping
  public RecommendationResponse recommend(@Valid @RequestBody RecommendationRequest request) {
    return recommendationService.recommend(request);
  }
}
