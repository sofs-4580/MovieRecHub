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
import psia.movieRecHub.dto.MovieGenre;

@Controller
public class MovieWebController {

  private final MovieServiceClient movieClient;
  private final CurrentUser currentUser;

  public MovieWebController(MovieServiceClient movieClient, CurrentUser currentUser) {
    this.movieClient = movieClient;
    this.currentUser = currentUser;
  }

  @ModelAttribute("genres")
  public MovieGenre[] genres() {
    return MovieGenre.values(); // para preencher o filtro de generos no template
  }

  @GetMapping("/")
  public String home(RedirectAttributes ra) {
    return "redirect:/movies";
  }

  @GetMapping("/movies")
  public String listMovies(@RequestParam(required = false) MovieGenre genre, Model model) {
    model.addAttribute("genre", genre);
    try {
      model.addAttribute("movies", movieClient.getMovies(genre));
    } catch (MovieServiceException ex) {
      model.addAttribute("movies", List.of());
      model.addAttribute("error", ex.getMessage());
    }
    return "movies";
  }

  @GetMapping("/movies/{id}")
  public String movieDetail(@PathVariable Long id, Model model, RedirectAttributes ra) {
    Long userId = currentUser.getId();
    try {
      model.addAttribute("movie", movieClient.getMovie(id));
      model.addAttribute("reviews", movieClient.getReviewsByMovie(id));
      if (userId != null) {
        model.addAttribute("lists", movieClient.getListsOfUser(userId, userId));
      }
      return "movie-detail";
    } catch (MovieServiceException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
      return "redirect:/movies";
    }
  }

  @PostMapping("/movies/{id}/review")
  public String addReview(
      @PathVariable Long id,
      @RequestParam int rating,
      @RequestParam(required = false) String comment,
      RedirectAttributes ra) {
    Long userId = currentUser.getId();
    if (userId == null) {
      return loginRedirect();
    }
    try {
      movieClient.createReview(userId, id, rating, comment);
      ra.addFlashAttribute("message", "Review added successfully!");
    } catch (MovieServiceException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/movies/" + id;
  }

  private String loginRedirect() {
    return "redirect:/login";
  }
}
