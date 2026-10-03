package psia.movieRecHub.entities;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import psia.movieRecHub.TestData;

class MovieListTest {

	private MovieList list;

	@BeforeEach
	void setUp() {
		list = new MovieList(1L, "lista", "desc", ListType.CUSTOM, ListVisibility.PUBLIC);
	}

	@Test
	void newListCreaion() {
		assertTrue(list.getItems().isEmpty());
		assertNotNull(list.getCreatedAt());
		assertEquals(1L, list.getOwnerId());
		assertEquals(ListType.CUSTOM, list.getType());
	}

	@Test
	void emptyListTest() {
		assertFalse(list.containsMovie(1L));
		assertFalse(list.removeMovie(1L));
		assertTrue(list.getItems().isEmpty());
	}

	@Test
	void isOwnedByMatchesOwner() {
		assertTrue(list.isOwnedBy(1L));
		assertFalse(list.isOwnedBy(2L));
		assertFalse(list.isOwnedBy(null));
	}

	@Test
	void addMovieAppendsAtEnd() {
		MovieListItem first = list.addMovie(TestData.movie(1L, "a"));
		MovieListItem second = list.addMovie(TestData.movie(2L, "b"));

		assertEquals(1, first.getPosition());
		assertEquals(2, second.getPosition());
		assertSame(list, first.getList());
		assertNotNull(first.getAddedAt());

		assertEquals(2, list.getItems().size());
		assertSame(first, list.getItems().get(0));
		assertSame(second, list.getItems().get(1));
	}

	@Test
	void containsMovieTest() {
		list.addMovie(TestData.movie(1L, "mov"));
		assertTrue(list.containsMovie(1L));
		assertFalse(list.containsMovie(9L));
	}

	@Test
	void removeMovieTest() {
		list.addMovie(TestData.movie(1L, "mov1"));
		list.addMovie(TestData.movie(2L, "mov2"));
		list.removeMovie(1L);

		assertFalse(list.containsMovie(1L));
		assertTrue(list.containsMovie(2L));
	}

	@Test
	void removeNonExistingMovie() {
		list.addMovie(TestData.movie(1L, "mov"));
		assertFalse(list.removeMovie(9L));
		assertEquals(1, list.getItems().size());
		assertEquals(1, list.getItems().get(0).getPosition());
	}

	@Test
	void removeMovieRenumbersPositions() {
		list.addMovie(TestData.movie(1L, "mov1"));
		list.addMovie(TestData.movie(2L, "mov2"));
		list.addMovie(TestData.movie(3L, "mov3"));

		boolean removed = list.removeMovie(2L);
		assertTrue(removed);
		assertEquals(2, list.getItems().size());

		assertEquals(1L, list.getItems().get(0).getMovie().getId());
		assertEquals(1, list.getItems().get(0).getPosition());
		assertEquals(3L, list.getItems().get(1).getMovie().getId());
		assertEquals(2, list.getItems().get(1).getPosition());
	}

	@Test
	void removeLastMovie() {
		list.addMovie(TestData.movie(1L, "mov1"));
		list.addMovie(TestData.movie(2L, "mov2"));

		assertTrue(list.removeMovie(2L));
		assertEquals(1, list.getItems().size());
		assertEquals(1, list.getItems().get(0).getPosition());
	}

	@Test
	void removeOnlyMovieTest() {
		list.addMovie(TestData.movie(1L, "mov1"));
		list.removeMovie(1L);

		assertTrue(list.getItems().isEmpty());
		assertFalse(list.containsMovie(1L));
		assertEquals(1, list.addMovie(TestData.movie(2L, "mov2")).getPosition());
	}

	@Test
	void positionsAfterAddingAfterARemove() {
		list.addMovie(TestData.movie(1L, "mov1"));
		list.addMovie(TestData.movie(2L, "mov2"));
		list.removeMovie(1L);

		MovieListItem added = list.addMovie(TestData.movie(3L, "mov3"));
		assertEquals(2, added.getPosition());
	}

}