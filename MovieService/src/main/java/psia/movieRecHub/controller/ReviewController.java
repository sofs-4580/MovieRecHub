package psia.movieRecHub.controller;

import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psia.movieRecHub.dto.request.ReviewRequestDto;
import psia.movieRecHub.dto.response.ReviewResponseDto;
import psia.movieRecHub.service.ReviewService;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

  private final ReviewService reviewService;

  public ReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  @PostMapping
  @ApiOperation(
      value = "Create review",
      notes = "Creates a review for a movie and updates the movie's average rating.")
  public ResponseEntity<ReviewResponseDto> createReview(@Valid @RequestBody ReviewRequestDto dto) {
    return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(dto));
  }

  @GetMapping("/movie/{movieId}")
  @ApiOperation(value = "Get reviews for a movie", notes = "Most recent first.")
  public ResponseEntity<List<ReviewResponseDto>> getByMovie(@PathVariable Long movieId) {
    return ResponseEntity.ok(reviewService.getReviewsByMovie(movieId));
  }

  @GetMapping("/user/{userId}")
  @ApiOperation(value = "Get reviews written by a user")
  public ResponseEntity<List<ReviewResponseDto>> getByUser(@PathVariable Long userId) {
    return ResponseEntity.ok(reviewService.getReviewsByUser(userId));
  }

  @DeleteMapping("/{id}")
  @ApiOperation(value = "Delete a review", notes = "Also recalculates the movie's average rating.")
  public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
    reviewService.deleteReview(id);
    return ResponseEntity.noContent().build();
  }
}
