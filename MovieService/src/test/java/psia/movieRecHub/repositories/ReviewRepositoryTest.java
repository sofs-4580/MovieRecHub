package psia.movieRecHub.repositories;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import psia.movieRecHub.TestData;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.Review;

// new Review(rating, comment, userId, movie)
@DataJpaTest
class ReviewRepositoryTest {

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private MovieRepository movieRepository;

	private Movie terminator;
	private Movie interstellar;

	@BeforeEach
	void setUp() {
		terminator = movieRepository.save(TestData.terminator());
		interstellar = movieRepository.save(TestData.interstellar());
	}

//findByIds

	@Test
	void findByMovieId() {
		reviewRepository.save(new Review(5, "bom", 1L, terminator));
		reviewRepository.save(new Review(4, "bom", 2L, terminator));
		reviewRepository.save(new Review(1, "mau", 1L, interstellar));

		List<Review> result = reviewRepository.findByMovieId(terminator.getId());
		assertEquals(2, result.size());
	}

	@Test
	void findByMovieIdWithoutReviews() {
		List<Review> result = reviewRepository.findByMovieId(terminator.getId());

		assertTrue(result.isEmpty());
	}

	@Test
	void findByUserId() {
		reviewRepository.save(new Review(5, "bom", 1L, terminator));
		reviewRepository.save(new Review(1, "mau", 1L, interstellar));
		reviewRepository.save(new Review(4, "bom", 2L, terminator));

		assertEquals(2, reviewRepository.findByUserId(1L).size());
		assertEquals(1, reviewRepository.findByUserId(2L).size());
		assertTrue(reviewRepository.findByUserId(99L).isEmpty());
	}

	@Test
	void findByUserAndMovieId() {
		Review review = reviewRepository.save(new Review(5, "bom", 1L, terminator));

		Optional<Review> result = reviewRepository.findByUserIdAndMovieId(1L, terminator.getId());

		assertTrue(result.isPresent());
		assertEquals(review.getId(), result.get().getId());
	}

	@Test
	void findByUserIdMovieIdNoReview() {
		reviewRepository.save(new Review(5, "bom", 1L, terminator));

		// user 1 nao avaliou o interstellar; user 2 nao avaliou terminator
		assertTrue(reviewRepository.findByUserIdAndMovieId(1L, interstellar.getId()).isEmpty());
		assertTrue(reviewRepository.findByUserIdAndMovieId(2L, terminator.getId()).isEmpty());
	}

//findByRating

	@Test
	void findByRating() {
		reviewRepository.save(new Review(5, "bom", 1L, terminator));
		reviewRepository.save(new Review(5, "bom", 2L, interstellar));
		reviewRepository.save(new Review(3, "medio", 3L, terminator));

		assertEquals(2, reviewRepository.findByRating(5).size());
		assertEquals(1, reviewRepository.findByRating(3).size());
		assertTrue(reviewRepository.findByRating(1).isEmpty());
	}

	@Test
	void findByMovieIdAndRating() {
		reviewRepository.save(new Review(5, "bom", 1L, terminator));
		reviewRepository.save(new Review(5, "bom", 2L, interstellar));
		reviewRepository.save(new Review(3, "medio", 3L, terminator));

		assertEquals(1, reviewRepository.findByMovieIdAndRating(terminator.getId(), 5).size());
		assertTrue(reviewRepository.findByMovieIdAndRating(terminator.getId(), 1).isEmpty());
	}

	@Test
	void reviewsOrderedByMostRecent() {
		Review old = new Review(1, "antiga", 1L, terminator);
		ReflectionTestUtils.setField(old, "createdAt", LocalDateTime.of(2026, 1, 1, 12, 0));
		reviewRepository.save(old);

		Review nova = new Review(3, "nova", 2L, terminator);
		ReflectionTestUtils.setField(nova, "createdAt", LocalDateTime.of(2026, 1, 3, 12, 0));
		reviewRepository.save(nova);

		Review meh = new Review(2, "do meio", 3L, terminator);
		ReflectionTestUtils.setField(meh, "createdAt", LocalDateTime.of(2026, 1, 2, 12, 0));
		reviewRepository.save(meh);

		List<Review> result = reviewRepository.findReviewsByMovieOrderedByMostRecent(terminator.getId());

		assertEquals(3, result.size());
		assertEquals("nova", result.get(0).getComment());
		assertEquals("do meio", result.get(1).getComment());
		assertEquals("antiga", result.get(2).getComment());
	}

	@Test
	void reviewsOrderedByMostRecentOnlyOneMovie() {
		reviewRepository.save(new Review(5, "bom", 1L, terminator));
		reviewRepository.save(new Review(1, "mau", 1L, interstellar));

		List<Review> result = reviewRepository.findReviewsByMovieOrderedByMostRecent(terminator.getId());

		assertEquals(1, result.size());
		assertEquals("bom", result.get(0).getComment());
	}

//regras da bd

	@Test
	void sameUserCannotReviewSameMovieTwice() {
		reviewRepository.saveAndFlush(new Review(5, "bom", 1L, terminator));

		assertThrows(DataIntegrityViolationException.class,
				() -> reviewRepository.saveAndFlush(new Review(2, "outra vez", 1L, terminator)));
	}

	@Test
	void sameUserCanReviewDifferentMovies() {
		reviewRepository.saveAndFlush(new Review(5, "bom", 1L, terminator));
		reviewRepository.saveAndFlush(new Review(2, "mau", 1L, interstellar));

		assertEquals(2, reviewRepository.count());
	}

	@Test
	void commentIsMandatory() {
		assertThrows(DataIntegrityViolationException.class,
				() -> reviewRepository.saveAndFlush(new Review(4, null, 1L, terminator)));
	}
}