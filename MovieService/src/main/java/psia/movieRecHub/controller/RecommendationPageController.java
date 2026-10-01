package psia.movieRecHub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Controller
public class RecommendationPageController {

  private final RestTemplate restTemplate;
  private final String recommendationServiceUrl;

  public RecommendationPageController(
      RestTemplate restTemplate,
      @Value("${recommendation.service.url:http://localhost:8083}")
          String recommendationServiceUrl) {
    this.restTemplate = restTemplate;
    this.recommendationServiceUrl = recommendationServiceUrl;
  }

  @GetMapping("/recommendations")
  public String page(@RequestParam(required = false) Long userId, Model model) {
    model.addAttribute("userId", userId);
    return "recommendations";
  }

  @PostMapping("/recommendations")
  public String recommend(@RequestParam Long userId, @RequestParam String prompt, Model model) {
    model.addAttribute("userId", userId);
    model.addAttribute("prompt", prompt);
    try {
      JsonNode response =
          restTemplate.postForObject(
              recommendationServiceUrl + "/api/recommendations",
              Map.of("userId", userId, "prompt", prompt),
              JsonNode.class);
      model.addAttribute("recommendations", response.path("recommendations").asText());
    } catch (RestClientException ex) {
      model.addAttribute("error", "Recommendation service is unavailable. Please try again later.");
    }
    return "recommendations";
  }
}
