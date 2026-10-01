package psia.movieRecHub.controller;

import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psia.movieRecHub.dto.request.ListRequestDto;
import psia.movieRecHub.dto.request.ListUpdateDto;
import psia.movieRecHub.dto.response.MovieListDetailDto;
import psia.movieRecHub.dto.response.MovieListSummaryDto;
import psia.movieRecHub.service.MovieListService;

/**
 * O utilizador autenticado vem no header X-User-Id (TODO: substituir por token/gateway quando o
 * UserService estiver integrado). Nos GET e opcional: sem ele so se veem listas publicas.
 */
@RestController
@RequestMapping("/api/lists")
public class MovieListController {

  private static final String USER_HEADER = "X-User-Id";

  private final MovieListService listService;

  public MovieListController(MovieListService listService) {
    this.listService = listService;
  }

  @PostMapping
  @ApiOperation(value = "Create a custom list (playlist)")
  public ResponseEntity<MovieListDetailDto> create(
      @RequestHeader(USER_HEADER) Long requesterId, @Valid @RequestBody ListRequestDto dto) {
    return ResponseEntity.status(HttpStatus.CREATED).body(listService.createList(requesterId, dto));
  }

  @GetMapping("/user/{userId}")
  @ApiOperation(
      value = "Lists of a user",
      notes = "Owner sees all lists; anyone else only sees the public ones.")
  public ResponseEntity<List<MovieListSummaryDto>> getByUser(
      @PathVariable Long userId,
      @RequestHeader(value = USER_HEADER, required = false) Long requesterId) {
    return ResponseEntity.ok(listService.getListsOfUser(userId, requesterId));
  }

  @GetMapping("/{id}")
  @ApiOperation(value = "Get a list with its movies")
  public ResponseEntity<MovieListDetailDto> getById(
      @PathVariable Long id,
      @RequestHeader(value = USER_HEADER, required = false) Long requesterId) {
    return ResponseEntity.ok(listService.getList(id, requesterId));
  }

  @PatchMapping("/{id}")
  @ApiOperation(value = "Update name, description or visibility (owner only)")
  public ResponseEntity<MovieListDetailDto> update(
      @PathVariable Long id,
      @RequestHeader(USER_HEADER) Long requesterId,
      @Valid @RequestBody ListUpdateDto dto) {
    return ResponseEntity.ok(listService.updateList(id, requesterId, dto));
  }

  @DeleteMapping("/{id}")
  @ApiOperation(value = "Delete a custom list (owner only)")
  public ResponseEntity<Void> delete(
      @PathVariable Long id, @RequestHeader(USER_HEADER) Long requesterId) {
    listService.deleteList(id, requesterId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/movies/{movieId}")
  @ApiOperation(value = "Add a movie to the end of a list (owner only)")
  public ResponseEntity<MovieListDetailDto> addMovie(
      @PathVariable Long id,
      @PathVariable Long movieId,
      @RequestHeader(USER_HEADER) Long requesterId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(listService.addMovie(id, movieId, requesterId));
  }

  @DeleteMapping("/{id}/movies/{movieId}")
  @ApiOperation(value = "Remove a movie from a list (owner only)")
  public ResponseEntity<Void> removeMovie(
      @PathVariable Long id,
      @PathVariable Long movieId,
      @RequestHeader(USER_HEADER) Long requesterId) {
    listService.removeMovie(id, movieId, requesterId);
    return ResponseEntity.noContent().build();
  }
}
