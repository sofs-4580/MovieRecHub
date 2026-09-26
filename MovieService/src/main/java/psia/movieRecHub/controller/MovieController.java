package psia.movieRecHub.controller;

import io.swagger.annotations.ApiOperation;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import psia.movieRecHub.dto.response.MovieResponseDto;
import psia.movieRecHub.entities.MovieGenre;
import psia.movieRecHub.service.MovieService;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

  private final MovieService movieService;

  public MovieController(MovieService movieService) {
    this.movieService = movieService;
  }

  @GetMapping
  @ApiOperation(
      value = "Get all movies",
      notes = "Returns a list of all movies, optionally filtered by genre.")
  public ResponseEntity<List<MovieResponseDto>> getAllMovies(
      @RequestParam(required = false) MovieGenre genre) {
    return ResponseEntity.ok(movieService.getAllMovies(genre));
  }

  @GetMapping("/{id}")
  @ApiOperation(value = "Get movie by id", notes = "Returns a single movie.")
  public ResponseEntity<MovieResponseDto> getMovieById(@PathVariable Long id) {
    return ResponseEntity.ok(movieService.getMovieById(id));
  }

  @GetMapping("/search")
  @ApiOperation(
      value = "Free-text search",
      notes = "Search movies by title, director or actor in a single query.")
  public ResponseEntity<List<MovieResponseDto>> search(@RequestParam String query) {
    return ResponseEntity.ok(movieService.search(query));
  }

  @GetMapping("/search/title")
  @ApiOperation(value = "Search movies by title")
  public ResponseEntity<List<MovieResponseDto>> searchByTitle(@RequestParam String title) {
    return ResponseEntity.ok(movieService.searchByTitle(title));
  }

  @GetMapping("/search/director")
  @ApiOperation(value = "Search movies by director")
  public ResponseEntity<List<MovieResponseDto>> searchByDirector(@RequestParam String director) {
    return ResponseEntity.ok(movieService.searchByDirector(director));
  }

  @GetMapping("/search/actor")
  @ApiOperation(value = "Search movies by actor")
  public ResponseEntity<List<MovieResponseDto>> searchByActor(@RequestParam String actor) {
    return ResponseEntity.ok(movieService.searchByActor(actor));
  }

  @GetMapping("/year/{releaseYear}")
  @ApiOperation(
      value = "Get movies by release year",
      notes = "Returns movies released in a given year.")
  public ResponseEntity<List<MovieResponseDto>> getByReleaseYear(@PathVariable int releaseYear) {
    return ResponseEntity.ok(movieService.getByReleaseYear(releaseYear));
  }

  @PatchMapping("/{id}/rating")
  @ApiOperation(
      value = "Recalculate average rating",
      notes = "Updates the average rating of a movie.")
  public ResponseEntity<Void> recalculateAvgRating(
      @PathVariable Long id, @RequestParam double newAvgRating) {
    movieService.recalculateAvgRating(id, newAvgRating);
    return ResponseEntity.noContent().build();
  }
}
