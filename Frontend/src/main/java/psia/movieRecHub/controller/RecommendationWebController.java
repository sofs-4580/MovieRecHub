package psia.movieRecHub.controller;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import psia.movieRecHub.client.ApiException;
import psia.movieRecHub.client.ProfileServiceClient;
import psia.movieRecHub.client.RecommendationServiceClient;
import psia.movieRecHub.client.RecommendationServiceException;
import psia.movieRecHub.config.CurrentUser;
import psia.movieRecHub.dto.OwnProfileDto;

@Controller
public class RecommendationWebController {

  private final RecommendationServiceClient recommendations;
  private final CurrentUser currentUser;
  private final ProfileServiceClient profiles;
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(RecommendationWebController.class);

  public RecommendationWebController(
      RecommendationServiceClient recommendations,
      CurrentUser currentUser,
      ProfileServiceClient profiles) {
    this.recommendations = recommendations;
    this.currentUser = currentUser;
    this.profiles = profiles;
  }

  @GetMapping("/recommendations")
  public String page(Model model) {
    Long userId = currentUser.getId();
    if (userId == null) {
      return "redirect:/login";
    }
    syncProfile(userId);
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

  // passa profile to recommendation service, tbm regista user para o agnte o tratar
  // se nada mudou, hash segue o mesmo, o agente nao faz nada
  private void syncProfile(Long userId) {
    try {
      OwnProfileDto profile = profiles.getOwn(currentUser.getSessionId());
      Set<String> genres = new HashSet<>();
      profile.favoriteGenres().forEach(g -> genres.add(g.name()));
      recommendations.saveProfile(userId, genres, profile.favoriteDecades());
    } catch (ApiException ex) {
      if (ex.isUnauthorized()) {
        throw ex;
      }
      // por exemplo PROFILE_NOT_READY logo a seguir ao registo tenta na proxima visita
      log.info("Profile not synced yet: {}", ex.getMessage());
    } catch (RecommendationServiceException ex) {
      log.warn("Could not send the profile to RecommendationService: {}", ex.getMessage());
    }
  }
}
