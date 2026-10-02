package psia.movieRecHub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import psia.movieRecHub.TestData;
import psia.movieRecHub.dto.request.ReviewRequestDto;
import psia.movieRecHub.dto.response.ReviewResponseDto;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.Review;
import psia.movieRecHub.exceptions.DuplicateReviewException;
import psia.movieRecHub.exceptions.MovieNotFoundException;
import psia.movieRecHub.exceptions.ReviewNotFoundException;
import psia.movieRecHub.repositories.MovieRepository;
import psia.movieRecHub.repositories.ReviewRepository;

// TestData.review(id, rating, userId, movie)
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

	@Mock
	private ReviewRepository reviewRepository;

	@Mock
	private MovieRepository movieRepository;

	@InjectMocks
	private ReviewService reviewService;

//createReview
	@Test
	void createReview() {
		Movie mov1 = TestData.movie(1L, "mov1");
		Review saved = TestData.review(10L, 4, 2L, mov1);
		when(movieRepository.findById(1L)).thenReturn(Optional.of(mov1));
		when(reviewRepository.findByUserIdAndMovieId(2L, 1L)).thenReturn(Optional.empty());
		when(reviewRepository.save(any(Review.class))).thenReturn(saved);

		ReviewRequestDto dto = new ReviewRequestDto();
		dto.setUserId(2L);
		dto.setMovieId(1L);
		dto.setRating(4);
		dto.setComment("bom");

		ReviewResponseDto result = reviewService.createReview(dto);

		assertEquals(10L, result.id());
		assertEquals(2L, result.userId());
		assertEquals(1L, result.movieId());
		assertEquals(4, result.rating());
		assertEquals(saved.getComment(), result.comment());
		assertNotNull(result.createdAt());
	}

	@Test
	void createReviewUpdatesAvgRating() {
		Movie mov1 = TestData.movie(1L, "mov1");
		mov1.setAvgRating(5.0);

		Review antiga = TestData.review(9L, 5, 3L, mov1);
		Review saved = TestData.review(10L, 4, 2L, mov1);
		when(movieRepository.findById(1L)).thenReturn(Optional.of(mov1));
		when(reviewRepository.findByUserIdAndMovieId(2L, 1L)).thenReturn(Optional.empty());
		when(reviewRepository.save(any(Review.class))).thenReturn(saved);

		// revs 5 e 4
		when(reviewRepository.findByMovieId(1L)).thenReturn(List.of(antiga, saved));

		ReviewRequestDto dto = new ReviewRequestDto();
		dto.setUserId(2L);
		dto.setMovieId(1L);
		dto.setRating(4);
		dto.setComment("bom");

		assertEquals(5.0, mov1.getAvgRating());

		reviewService.createReview(dto);

		assertEquals(4.5, mov1.getAvgRating());
		verify(movieRepository).save(mov1);
	}

	@Test
	void createReviewForNonExistingMovie() {
		when(movieRepository.findById(99L)).thenReturn(Optional.empty());

		ReviewRequestDto dto = new ReviewRequestDto();
		dto.setUserId(2L);
		dto.setMovieId(99L);
		dto.setRating(4);
		dto.setComment("bom");

		assertThrows(MovieNotFoundException.class, () -> reviewService.createReview(dto));
		verify(reviewRepository, never()).save(any());
	}

	@Test
	void createSameReviewTwice() {
		Movie mov1 = TestData.movie(1L, "mov1");
		Review existing = TestData.review(10L, 3, 2L, mov1);
		when(movieRepository.findById(1L)).thenReturn(Optional.of(mov1));
		when(reviewRepository.findByUserIdAndMovieId(2L, 1L)).thenReturn(Optional.of(existing));

		ReviewRequestDto dto = new ReviewRequestDto();
		dto.setUserId(2L);
		dto.setMovieId(1L);
		dto.setRating(5);
		dto.setComment("bom");

		assertThrows(DuplicateReviewException.class, () -> reviewService.createReview(dto));
		verify(reviewRepository, never()).save(any());
		verify(movieRepository, never()).save(any());
	}

//getReviews

	@Test
	void getReviewsByMovie() {
		Movie mov1 = TestData.movie(1L, "mov1");
		Review rev1 = TestData.review(11L, 5, 3L, mov1);
		Review rev2 = TestData.review(10L, 2, 2L, mov1);
		when(reviewRepository.findReviewsByMovieOrderedByMostRecent(1L)).thenReturn(List.of(rev1, rev2));

		List<ReviewResponseDto> result = reviewService.getReviewsByMovie(1L);

		assertEquals(2, result.size());
		assertEquals(11L, result.get(0).id());
		assertEquals(3L, result.get(0).userId());
		assertEquals(1L, result.get(0).movieId());
		assertEquals(5, result.get(0).rating());
		assertEquals(rev1.getComment(), result.get(0).comment());
		assertEquals(rev1.getCreatedAt(), result.get(0).createdAt());

		assertEquals(10L, result.get(1).id());
		assertEquals(2L, result.get(1).userId());
		assertEquals(1L, result.get(1).movieId());
		assertEquals(5, result.get(0).rating());
		assertEquals(rev2.getComment(), result.get(1).comment());
		assertEquals(rev2.getCreatedAt(), result.get(1).createdAt());

	}

	@Test
	void getReviewsByMovieWithoutReviews() {
		when(reviewRepository.findReviewsByMovieOrderedByMostRecent(1L)).thenReturn(List.of());

		List<ReviewResponseDto> result = reviewService.getReviewsByMovie(1L);

		assertTrue(result.isEmpty());
	}

	@Test
	void getReviewsByUser() {
		Movie mov1 = TestData.movie(1L, "mov1");
		Movie mov2 = TestData.movie(2L, "mov2");
		Review rev1 = TestData.review(10L, 4, 2L, mov1);
		Review rev2 = TestData.review(11L, 3, 2L, mov2);
		when(reviewRepository.findByUserId(2L)).thenReturn(List.of(rev1, rev2));

		List<ReviewResponseDto> result = reviewService.getReviewsByUser(2L);

		assertEquals(2, result.size());
		assertEquals(1L, result.get(0).movieId());
		assertEquals(2L, result.get(0).userId());
		assertEquals(2L, result.get(1).movieId());
		assertEquals(2L, result.get(1).userId());
	}

//deleteReview

	@Test
	void deleteReview() {
		Movie mov1 = TestData.movie(1L, "mov1");
		mov1.setAvgRating(3.0);
		Review toDelete = TestData.review(10L, 1, 2L, mov1);
		Review remaining = TestData.review(11L, 5, 3L, mov1);
		when(reviewRepository.findById(10L)).thenReturn(Optional.of(toDelete));
		when(reviewRepository.findByMovieId(1L)).thenReturn(List.of(remaining));

		reviewService.deleteReview(10L);

		verify(reviewRepository).delete(toDelete);
		assertEquals(5.0, mov1.getAvgRating());
		verify(movieRepository).save(mov1);
	}

	@Test
	void deleteLastReviewOfMovie() {
		Movie mov1 = TestData.movie(1L, "mov1");
		mov1.setAvgRating(4.0);
		Review only = TestData.review(10L, 4, 2L, mov1);
		when(reviewRepository.findById(10L)).thenReturn(Optional.of(only));
		when(reviewRepository.findByMovieId(1L)).thenReturn(List.of());

		reviewService.deleteReview(10L);

		// sem reviews fica a 0
		assertEquals(0.0, mov1.getAvgRating());
		verify(movieRepository).save(mov1);
	}

	@Test
	void deleteReviewThatDoesNotExist() {
		when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(ReviewNotFoundException.class, () -> reviewService.deleteReview(99L));
		verify(reviewRepository, never()).delete(any());
		verify(movieRepository, never()).save(any());
	}
}