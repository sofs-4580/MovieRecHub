package psia.movieRecHub.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import psia.movieRecHub.dto.request.ReviewRequestDto;
import psia.movieRecHub.dto.response.ReviewResponseDto;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.Review;
import psia.movieRecHub.exceptions.DuplicateReviewException;
import psia.movieRecHub.exceptions.MovieNotFoundException;
import psia.movieRecHub.exceptions.ReviewNotFoundException;
import psia.movieRecHub.repositories.MovieRepository;
import psia.movieRecHub.repositories.ReviewRepository;

@Service
public class ReviewService {

	@Autowired
	private ReviewRepository reviewRepository;
	@Autowired
	private MovieRepository movieRepository;

	public ReviewResponseDto createReview(ReviewRequestDto dto) {

		if (dto.getRating() < 1 || dto.getRating() > 5) {
			throw new IllegalArgumentException("Rating must be between 1 and 5");
		}
		
		Movie movie = movieRepository.findById(dto.getMovieId())
				.orElseThrow(() -> new MovieNotFoundException(dto.getMovieId()));

		reviewRepository.findByUserIdAndMovieId(dto.getUserId(), dto.getMovieId()).ifPresent(r -> {
			throw new DuplicateReviewException(dto.getUserId(), dto.getMovieId());
		});

		Review review = new Review(dto.getRating(), dto.getComment(), dto.getUserId(), movie);
		Review saved = reviewRepository.save(review);

		recalculateAvgRating(movie);

		return mapToResponse(saved);
	}

	public List<ReviewResponseDto> getReviewsByMovie(Long movieId) {
		return reviewRepository.findReviewsByMovieOrderedByMostRecent(movieId).stream().map(this::mapToResponse)
				.toList();
	}

	public List<ReviewResponseDto> getReviewsByUser(Long userId) {
		return reviewRepository.findByUserId(userId).stream().map(this::mapToResponse).toList();
	}

	public void deleteReview(Long reviewId) {
		Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new ReviewNotFoundException(reviewId));
		Movie movie = review.getMovie();
		reviewRepository.delete(review);
		recalculateAvgRating(movie);
	}

	// Recalcula e persiste o avgRating do filme a partir de todas as reviews
	// atuais.
	private void recalculateAvgRating(Movie movie) {
		List<Review> reviews = reviewRepository.findByMovieId(movie.getId());
		double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
		movie.setAvgRating(avg);
		movieRepository.save(movie);
	}

	private ReviewResponseDto mapToResponse(Review review) {
		return new ReviewResponseDto(review.getId(), review.getUserId(), review.getMovie().getId(), review.getRating(),
				review.getComment(), review.getCreatedAt());
	}
}
