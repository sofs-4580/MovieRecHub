package psia.movieRecHub.controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import psia.movieRecHub.client.MovieServiceClient;
import psia.movieRecHub.client.MovieServiceException;
import psia.movieRecHub.config.CurrentUser;
import psia.movieRecHub.dto.ListVisibility;
import psia.movieRecHub.dto.MovieListDetailDto;

@Controller
public class MovieListWebController {

  private final MovieServiceClient movieClient;
  private final CurrentUser currentUser;

  public MovieListWebController(MovieServiceClient movieClient, CurrentUser currentUser) {
    this.movieClient = movieClient;
    this.currentUser = currentUser;
  }

  @ModelAttribute("visibilities")
  public ListVisibility[] visibilities() {
    return ListVisibility.values();
  }

  @GetMapping("/lists")
  public String myLists(Model model) {
    Long userId = currentUser.getId();
    if (userId == null) {
      model.addAttribute("lists", List.of());
      return "lists";
    }
    try {
      model.addAttribute("lists", movieClient.getListsOfUser(userId, userId));
    } catch (MovieServiceException ex) {
      model.addAttribute("error", ex.getMessage());
    }
    return "lists";
  }

  @PostMapping("/lists")
  public String createList(
      @RequestParam String name,
      @RequestParam(required = false) String description,
      @RequestParam ListVisibility visibility,
      RedirectAttributes ra) {
    Long userId = currentUser.getId();
    if (userId == null) {
      return loginRedirect();
    }
    try {
      movieClient.createList(userId, name, description, visibility);
      ra.addFlashAttribute("message", "List created!");
    } catch (MovieServiceException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/lists";
  }

  @GetMapping("/lists/{id}")
  public String listDetail(@PathVariable Long id, Model model, RedirectAttributes ra) {
    Long userId = currentUser.getId();
    try {
      MovieListDetailDto list = movieClient.getList(id, userId);
      model.addAttribute("list", list);
      model.addAttribute("isOwner", userId != null && userId.equals(list.ownerId()));
      return "list-detail";
    } catch (MovieServiceException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
      return "redirect:/lists";
    }
  }

  @PostMapping("/lists/add")
  public String addMovie(
      @RequestParam("listId") Long id, @RequestParam Long movieId, RedirectAttributes ra) {
    Long userId = currentUser.getId();
    if (userId == null) {
      return loginRedirect();
    }
    try {
      movieClient.addMovieToList(id, movieId, userId);
      ra.addFlashAttribute("message", "Movie added to list!");
    } catch (MovieServiceException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/movies/" + movieId;
  }

  @PostMapping("/lists/{id}/remove")
  public String removeMovie(
      @PathVariable Long id, @RequestParam Long movieId, RedirectAttributes ra) {
    Long userId = currentUser.getId();
    if (userId == null) {
      return loginRedirect();
    }
    try {
      movieClient.removeMovieFromList(id, movieId, userId);
      ra.addFlashAttribute("message", "Movie removed.");
    } catch (MovieServiceException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/lists/" + id;
  }

  @PostMapping("/lists/{id}/delete")
  public String deleteList(@PathVariable Long id, RedirectAttributes ra) {
    Long userId = currentUser.getId();
    if (userId == null) {
      return loginRedirect();
    }
    try {
      movieClient.deleteList(id, userId);
      ra.addFlashAttribute("message", "List deleted.");
    } catch (MovieServiceException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/lists";
  }

  private String loginRedirect() {
    return "redirect:/login";
  }
}
