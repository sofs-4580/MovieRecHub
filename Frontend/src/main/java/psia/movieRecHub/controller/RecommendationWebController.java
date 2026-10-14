package psia.movieRecHub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import psia.movieRecHub.client.RecommendationServiceClient;
import psia.movieRecHub.client.RecommendationServiceException;
import psia.movieRecHub.config.CurrentUser;

@Controller
public class RecommendationWebController {

  private final RecommendationServiceClient recommendations;
  private final CurrentUser currentUser;

  public RecommendationWebController(
      RecommendationServiceClient recommendations, CurrentUser currentUser) {
    this.recommendations = recommendations;
    this.currentUser = currentUser;
  }

  @GetMapping("/recommendations")
  public String page(Model model) {
    Long userId = currentUser.getId();
    if (userId == null) {
      return "redirect:/login";
    }
    loadForYou(userId, model);
    return "recommendations";
  }

  @PostMapping("/recommendations")
  public String ask(@RequestParam(defaultValue = "") String text, Model model) {
    Long userId = currentUser.getId();
    if (userId == null) {
      return "redirect:/login";
    }
    String request = text.trim();
    model.addAttribute("askText", request);
    if (request.isEmpty()) {
      model.addAttribute("error", "Write what you feel like watching.");
    } else {
      try {
        model.addAttribute("answer", recommendations.ask(userId, request));
      } catch (RecommendationServiceException ex) {
        model.addAttribute("error", ex.getMessage());
      }
    }
    loadForYou(userId, model);
    return "recommendations";
  }

  private void loadForYou(Long userId, Model model) {
    try {
      model.addAttribute("forYou", recommendations.forUser(userId));
    } catch (RecommendationServiceException ex) {
      model.addAttribute("forYouError", ex.getMessage());
    }
  }
}
