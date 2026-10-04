package psia.movieRecHub.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import psia.movieRecHub.client.ApiException;
import psia.movieRecHub.client.MovieServiceClient;
import psia.movieRecHub.client.MovieServiceException;
import psia.movieRecHub.client.ProfileServiceClient;
import psia.movieRecHub.config.CurrentUser;
import psia.movieRecHub.dto.MovieGenre;
import psia.movieRecHub.dto.OwnProfileDto;
import psia.movieRecHub.dto.PublicProfileDto;

@Controller
public class ProfileWebController {

  /** Decades offered in the form. Others (settable through the API) are kept untouched on save. */
  private static final List<Integer> DECADES =
      List.of(1920, 1930, 1940, 1950, 1960, 1970, 1980, 1990, 2000, 2010, 2020);

  private final ProfileServiceClient profiles;
  private final MovieServiceClient movieClient;
  private final CurrentUser currentUser;

  public ProfileWebController(
      ProfileServiceClient profiles, MovieServiceClient movieClient, CurrentUser currentUser) {
    this.profiles = profiles;
    this.movieClient = movieClient;
    this.currentUser = currentUser;
  }

  // ---------- browse other people ----------

  @GetMapping("/profiles")
  public String browse(
      @RequestParam(required = false) String username,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    if (currentUser.getId() == null) {
      return "redirect:/login";
    }
    String query = username == null ? "" : username.trim().toLowerCase();
    model.addAttribute("query", query);
    try {
      model.addAttribute(
          "result", profiles.browse(currentUser.getSessionId(), query, Math.max(page, 0), 12));
    } catch (ApiException ex) {
      if (ex.isUnauthorized()) {
        throw ex;
      }
      model.addAttribute("error", ex.getMessage());
    }
    return "profiles";
  }

  @GetMapping("/profiles/{id}")
  public String detail(@PathVariable long id, Model model, RedirectAttributes ra) {
    Long me = currentUser.getId();
    if (me == null) {
      return "redirect:/login";
    }
    PublicProfileDto profile;
    try {
      profile = profiles.get(currentUser.getSessionId(), id);
    } catch (ApiException ex) {
      if (ex.getStatus() == 404) {
        ra.addFlashAttribute("error", "That profile does not exist.");
        return "redirect:/profiles";
      }
      throw ex;
    }
    model.addAttribute("profile", profile);
    model.addAttribute("isMe", me.equals(id));
    try {
      // For someone else this only returns their public lists.
      model.addAttribute("lists", movieClient.getListsOfUser(id, me));
    } catch (MovieServiceException ex) {
      model.addAttribute("lists", List.of());
    }
    return "profile-detail";
  }

  // ---------- my profile ----------

  @GetMapping("/profile")
  public String myProfile(Model model) {
    if (currentUser.getId() == null) {
      return "redirect:/login";
    }
    try {
      return editForm(model, profiles.getOwn(currentUser.getSessionId()));
    } catch (ApiException ex) {
      // UserService creates the profile in the background right after registration.
      if ("PROFILE_NOT_READY".equals(ex.getCode())) {
        model.addAttribute("notReady", true);
        return "my-profile";
      }
      throw ex;
    }
  }

  @PostMapping("/profile")
  public String saveProfile(
      @RequestParam(defaultValue = "") String displayName,
      @RequestParam(defaultValue = "") String bio,
      @RequestParam(name = "favoriteGenres", required = false) List<MovieGenre> favoriteGenres,
      @RequestParam(name = "favoriteDecades", required = false) List<Integer> favoriteDecades,
      Model model,
      RedirectAttributes ra) {
    Long me = currentUser.getId();
    if (me == null) {
      return "redirect:/login";
    }
    String sessionId = currentUser.getSessionId();
    Set<MovieGenre> genres = new HashSet<>(favoriteGenres == null ? List.of() : favoriteGenres);

    String problem = null;
    if (genres.isEmpty()) {
      problem = "Keep at least one favourite genre.";
    } else if (displayName.length() > 100) {
      problem = "The display name can have at most 100 characters.";
    } else if (bio.length() > 2000) {
      problem = "The bio can have at most 2000 characters.";
    }

    Set<Integer> decades = new HashSet<>();
    if (favoriteDecades != null) {
      favoriteDecades.stream().filter(DECADES::contains).forEach(decades::add);
    }
    if (problem == null) {
      OwnProfileDto current = profiles.getOwn(sessionId);
      current.favoriteDecades().stream().filter(d -> !DECADES.contains(d)).forEach(decades::add);
      try {
        profiles.updateOwn(sessionId, displayName.trim(), bio, genres, decades);
        ra.addFlashAttribute("message", "Profile saved.");
        return "redirect:/profile";
      } catch (ApiException ex) {
        if (ex.isUnauthorized()) {
          throw ex;
        }
        problem = ex.getMessage();
      }
    }
    // Show the form again with what the person typed.
    model.addAttribute("error", problem);
    return editForm(model, new OwnProfileDto(me, displayName, bio, genres, decades));
  }

  private String editForm(Model model, OwnProfileDto profile) {
    model.addAttribute("profile", profile);
    model.addAttribute("genres", MovieGenre.values());
    model.addAttribute("decades", new ArrayList<>(DECADES));
    return "my-profile";
  }
}
