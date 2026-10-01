package psia.movieRecHub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import psia.movieRecHub.dto.request.ReviewRequestDto;
import psia.movieRecHub.entities.MovieGenre;
import psia.movieRecHub.service.MovieListService;
import psia.movieRecHub.service.MovieService;
import psia.movieRecHub.service.ReviewService;

@Controller
public class MovieWebController {

  private final MovieService movieService;
  private final ReviewService reviewService;
  private final MovieListService listService;

  public MovieWebController(
      MovieService movieService, ReviewService reviewService, MovieListService listService) {
    this.movieService = movieService;
    this.reviewService = reviewService;
    this.listService = listService;
  }

  @ModelAttribute("genres")
  public MovieGenre[] genres() {
    return MovieGenre.values(); // para preencher o <select> de filtro no template
  }

  @GetMapping("/movies")
  public String listMovies(@RequestParam(required = false) MovieGenre genre, Model model) {
    model.addAttribute("genre", genre);
    model.addAttribute("movies", movieService.getAllMovies(genre));
    return "movies"; // devolve o templates/movies.html
  }

  @GetMapping("/movies/{id}")
  public String movieDetail(
      @PathVariable Long id, @RequestParam(required = false) Long userId, Model model) {
    try {
      model.addAttribute("movie", movieService.getMovieById(id));
      model.addAttribute("reviews", reviewService.getReviewsByMovie(id));
      model.addAttribute("userId", userId);
      if (userId != null) { // TODO: sessao; por agora o userId vem na query string
        model.addAttribute("lists", listService.getListsOfUser(userId, userId));
      }
      return "movie-detail";
    } catch (RuntimeException ex) {
      model.addAttribute("error", ex.getMessage());
      return "redirect:/movies";
    }
  }

  @PostMapping("/movies/{id}/review")
  public String addReview(
      @PathVariable Long id,
      @RequestParam
          Long
              userId, // TODO: trocar por sessão/token quando a integração com o UserService estiver
      // pronta
      @RequestParam int rating,
      @RequestParam(required = false) String comment,
      RedirectAttributes ra) {
    try {
      ReviewRequestDto dto = new ReviewRequestDto();
      dto.setUserId(userId);
      dto.setMovieId(id);
      dto.setRating(rating);
      dto.setComment(comment);
      reviewService.createReview(dto);
      ra.addFlashAttribute("message", "Review added successfully!");
    } catch (RuntimeException ex) {
      ra.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/movies/" + id;
  }
}
