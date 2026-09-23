package pt.ul.fc.css.tascaeats.delivery.controller;

import io.swagger.annotations.ApiOperation;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

  // private final MovieService movieHandler;

  public MovieController(MovieService movieService) {
    this.movieHandler = movieService;
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
}
