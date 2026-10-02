package psia.movieRecHub;

import java.util.Set;
import org.springframework.test.util.ReflectionTestUtils;
import psia.movieRecHub.entities.ListType;
import psia.movieRecHub.entities.ListVisibility;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.MovieGenre;
import psia.movieRecHub.entities.MovieList;
import psia.movieRecHub.entities.Review;

/**
 * Fabrica para testes
 */
public final class TestData {

	private TestData() {
	}

	public static Movie newMovie(String title, int year, String director, String actors, MovieGenre genre) {
		return new Movie(title, year, new java.util.HashSet<>(Set.of(genre)), "Synopsis of " + title, director, actors);
	}

	public static Movie movie(Long id, String title) {
		Movie movie = newMovie(title, 1999, "dir", "sara, sofia", MovieGenre.DRAMA);
		ReflectionTestUtils.setField(movie, "id", id);
		return movie;
	}

	public static Review review(Long id, int rating, Long userId, Movie movie) {
		Review review = new Review(rating, "comment " + rating, userId, movie);
		ReflectionTestUtils.setField(review, "id", id);
		return review;
	}

	public static MovieList list(Long id, Long ownerId, String name, ListType type, ListVisibility visibility) {
		MovieList list = new MovieList(ownerId, name, "desc", type, visibility);
		ReflectionTestUtils.setField(list, "id", id);
		return list;
	}
}
