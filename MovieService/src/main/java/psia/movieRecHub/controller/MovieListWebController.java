package psia.movieRecHub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import psia.movieRecHub.dto.request.ListRequestDto;
import psia.movieRecHub.entities.ListVisibility;
import psia.movieRecHub.service.MovieListService;

/**
 * UI simples das listas. TODO: o userId vem por parametro ate haver sessao/token do UserService.
 */
@Controller
public class MovieListWebController {

  private final MovieListService listService;

  public MovieListWebController(MovieListService listService) {
    this.listService = listService;
  }

  @ModelAttribute("visibilities")
  public ListVisibility[] visibilities() {
    return ListVisibility.values();
  }

  @GetMapping("/lists")
  public String myLists(@RequestParam(required = false) Long userId, Model model) {
    model.addAttribute("userId", userId);
    if (userId != null) {
      model.addAttribute("lists", listService.getListsOfUser(userId, userId));
    }
    return "lists";
  }

  @PostMapping("/lists")
  public String createList(
      @RequestParam Long userId,
      @RequestParam String name,
      @RequestParam(required = false) String description,
      @RequestParam ListVisibility visibility,
      RedirectAttributes ra) {
    try {
      listService.createList(userId, new ListRequestDto(name, description, visibility));
      ra.addFlashAttribute("message", "List created!");
    } catch (RuntimeException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/lists?userId=" + userId;
  }

  // userId aqui e quem esta a ver (para listas privadas so o dono ve)
  @GetMapping("/lists/{id}")
  public String listDetail(
      @PathVariable Long id,
      @RequestParam(required = false) Long userId,
      Model model,
      RedirectAttributes ra) {
    try {
      var list = listService.getList(id, userId);
      model.addAttribute("list", list);
      model.addAttribute("userId", userId);
      model.addAttribute("isOwner", userId != null && userId.equals(list.ownerId()));
      return "list-detail";
    } catch (RuntimeException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
      return "redirect:/lists" + (userId != null ? "?userId=" + userId : "");
    }
  }

  @PostMapping("/lists/add")
  public String addMovie(
      @RequestParam("listId") Long id,
      @RequestParam Long userId,
      @RequestParam Long movieId,
      RedirectAttributes ra) {
    try {
      listService.addMovie(id, movieId, userId);
      ra.addFlashAttribute("message", "Movie added to list!");
    } catch (RuntimeException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/movies/" + movieId + "?userId=" + userId;
  }

  @PostMapping("/lists/{id}/remove")
  public String removeMovie(
      @PathVariable Long id,
      @RequestParam Long userId,
      @RequestParam Long movieId,
      RedirectAttributes ra) {
    try {
      listService.removeMovie(id, movieId, userId);
      ra.addFlashAttribute("message", "Movie removed.");
    } catch (RuntimeException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/lists/" + id + "?userId=" + userId;
  }

  @PostMapping("/lists/{id}/delete")
  public String deleteList(
      @PathVariable Long id, @RequestParam Long userId, RedirectAttributes ra) {
    try {
      listService.deleteList(id, userId);
      ra.addFlashAttribute("message", "List deleted.");
    } catch (RuntimeException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/lists?userId=" + userId;
  }
}
