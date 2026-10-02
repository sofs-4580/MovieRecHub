package psia.movieRecHub.repositories;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import psia.movieRecHub.entities.ListType;
import psia.movieRecHub.entities.ListVisibility;
import psia.movieRecHub.TestData;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.MovieList;

@DataJpaTest
class MovieListRepositoryTest {

	@Autowired
	private MovieListRepository listRepository;

	@Autowired
	private MovieRepository movieRepository;

	// so e usado para o flush/clear (obriga a ir buscar outra vez a BD)
	@Autowired
	private TestEntityManager em;

	private Movie terminator;
	private Movie interstellar;
	private Movie avengers;
	private Movie mammaMia;

	@BeforeEach
	void setUp() {
		terminator = movieRepository.save(TestData.terminator());
		interstellar = movieRepository.save(TestData.interstellar());
		avengers = movieRepository.save(TestData.avengers());
		mammaMia = movieRepository.save(TestData.mammaMia());
	}

//findByOwnerId

	@Test
	void findByOwner() {
		listRepository.save(new MovieList(1L, "lista1", "desc", ListType.CUSTOM, ListVisibility.PUBLIC));
		listRepository.save(new MovieList(1L, "lista2", "desc", ListType.CUSTOM, ListVisibility.PRIVATE));
		listRepository.save(new MovieList(2L, "de outro", "desc", ListType.CUSTOM, ListVisibility.PUBLIC));

		List<MovieList> result = listRepository.findByOwnerIdOrderByCreatedAtAsc(1L);
		assertEquals(2, result.size());
	}

	@Test
	void findByOwnerOrdersByOldest() {
		MovieList nova = new MovieList(1L, "nova", "desc", ListType.CUSTOM, ListVisibility.PUBLIC);
		ReflectionTestUtils.setField(nova, "createdAt", LocalDateTime.of(2026, 1, 3, 12, 0));
		
		MovieList old = new MovieList(1L, "antiga", "desc", ListType.CUSTOM, ListVisibility.PUBLIC);
		ReflectionTestUtils.setField(old, "createdAt", LocalDateTime.of(2026, 1, 1, 12, 0));
		listRepository.save(nova);
		listRepository.save(old);

		List<MovieList> result = listRepository.findByOwnerIdOrderByCreatedAtAsc(1L);

		assertEquals("antiga", result.get(0).getName());
		assertEquals("nova", result.get(1).getName());
	}

	@Test
	void findByOwnerAndVisibility() {
		listRepository.save(new MovieList(1L, "privada", "desc", ListType.CUSTOM, ListVisibility.PRIVATE));
		listRepository.save(new MovieList(1L, "publica", "desc", ListType.CUSTOM, ListVisibility.PUBLIC));

		List<MovieList> result = listRepository.findByOwnerIdAndVisibilityOrderByCreatedAtAsc(1L,
				ListVisibility.PUBLIC);

		assertEquals(1, result.size());
		assertEquals("publica", result.get(0).getName());
	}

//exists

	@Test
	void existsByOwnerAndName() {
		listRepository.save(new MovieList(1L, "lista", "desc", ListType.CUSTOM, ListVisibility.PUBLIC));

		assertTrue(listRepository.existsByOwnerIdAndName(1L, "lista"));
		assertFalse(listRepository.existsByOwnerIdAndName(1L, "outra"));
		assertFalse(listRepository.existsByOwnerIdAndName(2L, "lista"));
	}

	@Test
	void existsByOwnerAndType() {
		listRepository.save(new MovieList(1L, "Watchlist", "desc", ListType.WATCHLIST, ListVisibility.PRIVATE));

		assertTrue(listRepository.existsByOwnerIdAndType(1L, ListType.WATCHLIST));
		assertFalse(listRepository.existsByOwnerIdAndType(1L, ListType.FAVORITES));
		assertFalse(listRepository.existsByOwnerIdAndType(2L, ListType.WATCHLIST));
	}

//regras da bd

	@Test
	void sameOwnerCantRepeatName() {
		listRepository.saveAndFlush(new MovieList(1L, "lista", "desc", ListType.CUSTOM, ListVisibility.PUBLIC));

		assertThrows(DataIntegrityViolationException.class, () -> listRepository
				.saveAndFlush(new MovieList(1L, "lista", "desc", ListType.CUSTOM, ListVisibility.PRIVATE)));
	}

	@Test
	void differentOwnersCanReapeatName() {
		listRepository.saveAndFlush(new MovieList(1L, "lista", "desc", ListType.CUSTOM, ListVisibility.PUBLIC));
		listRepository.saveAndFlush(new MovieList(2L, "lista", "desc", ListType.CUSTOM, ListVisibility.PUBLIC));

		assertEquals(2, listRepository.count());
	}

	@Test
	void nameIsMandatory() {
		assertThrows(DataIntegrityViolationException.class, () -> listRepository
				.saveAndFlush(new MovieList(1L, null, "desc", ListType.CUSTOM, ListVisibility.PUBLIC)));
	}

	@Test
	void descriptionIsOptional() {
		MovieList list = listRepository
				.saveAndFlush(new MovieList(1L, "lista", null, ListType.CUSTOM, ListVisibility.PUBLIC));

		assertNotNull(list.getId());
	}

//movies

	@Test
	void moviesAreSavedWithList() {
		MovieList list = new MovieList(1L, "lista", "desc", ListType.CUSTOM, ListVisibility.PUBLIC);
		list.addMovie(terminator);
		list.addMovie(interstellar);
		listRepository.saveAndFlush(list);
		em.clear();

		MovieList result = listRepository.findById(list.getId()).get();

		assertEquals(2, result.getItems().size());
		
		assertEquals("Terminator 2: Judgment Day", result.getItems().get(0).getMovie().getTitle());
		assertEquals(1, result.getItems().get(0).getPosition());
		
		assertEquals("Interstellar", result.getItems().get(1).getMovie().getTitle());
		assertEquals(2, result.getItems().get(1).getPosition());
	}

	@Test
	void removeMovieFromList() {
		MovieList list = new MovieList(1L, "lista", "desc", ListType.CUSTOM, ListVisibility.PUBLIC);
		list.addMovie(terminator);
		list.addMovie(interstellar);
		listRepository.saveAndFlush(list);

		list.removeMovie(terminator.getId());
		listRepository.saveAndFlush(list);
		em.clear();

		MovieList result = listRepository.findById(list.getId()).get();
		assertEquals(1, result.getItems().size());
		assertEquals("Interstellar", result.getItems().get(0).getMovie().getTitle());
		assertEquals(1, result.getItems().get(0).getPosition());
	}

	@Test
	void sameMovieCannotBeTwiceInList() {
		MovieList list = new MovieList(1L, "lista", "desc", ListType.CUSTOM, ListVisibility.PUBLIC);
		list.addMovie(terminator);
		list.addMovie(terminator);

		assertThrows(DataIntegrityViolationException.class, () -> listRepository.saveAndFlush(list));
	}

	@Test
	void deleteListKeepsMovies() {
		MovieList list = new MovieList(1L, "lista", "desc", ListType.CUSTOM, ListVisibility.PUBLIC);
		list.addMovie(terminator);
		listRepository.saveAndFlush(list);

		listRepository.delete(list);
		listRepository.flush();
		em.clear();

		// lista desaparece,movies ainda existem
		assertFalse(listRepository.existsById(list.getId()));
		assertEquals(4, movieRepository.count());
	}
}