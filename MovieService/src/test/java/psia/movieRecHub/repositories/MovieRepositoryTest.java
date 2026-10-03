package psia.movieRecHub.repositories;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import psia.movieRecHub.TestData;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.MovieGenre;

@DataJpaTest
class MovieRepositoryTest {

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
		em.flush();
		em.clear();
	}

//save

	@Test
	void saveMovie() {
		Movie result = movieRepository.findById(terminator.getId()).get();

		assertEquals("Terminator 2: Judgment Day", result.getTitle());
		assertEquals(1991, result.getReleaseYear());
		assertEquals("James Cameron", result.getDirector());
		assertEquals(2, result.getGenres().size());
		assertTrue(result.getGenres().contains(MovieGenre.SCI_FI));
		assertTrue(result.getGenres().contains(MovieGenre.ACTION));

		assertEquals(0.0, result.getAvgRating());
	}

//findByGenresContaining

	@Test
	void findByGenre() {
		List<Movie> result = movieRepository.findByGenresContaining(MovieGenre.COMEDY);

		assertEquals(1, result.size());
		assertEquals("Mamma Mia!", result.get(0).getTitle());
	}

	@Test
	void findByGenreManyMovies() {
		// terminator 2 interstellar avengers 
		List<Movie> sciFi = movieRepository.findByGenresContaining(MovieGenre.SCI_FI);
		// terminator 2 e avengers 
		List<Movie> action = movieRepository.findByGenresContaining(MovieGenre.ACTION);
		//só mama mia <3
		List<Movie> rom = movieRepository.findByGenresContaining(MovieGenre.ROMANCE);


		assertEquals(3, sciFi.size());
		assertEquals(2, action.size());
	}

	@Test
	void findByGenreWithNoMovies() {
		List<Movie> result = movieRepository.findByGenresContaining(MovieGenre.HORROR);

		assertTrue(result.isEmpty());
	}

//findByReleaseYear

	@Test
	void findByReleaseYear() {
		List<Movie> result = movieRepository.findByReleaseYear(2014);

		assertEquals(1, result.size());
		assertEquals(interstellar.getId(), result.get(0).getId());
	}

	@Test
	void findByReleaseYearWithNoMovies() {
		List<Movie> result = movieRepository.findByReleaseYear(1800);

		assertTrue(result.isEmpty());
	}

//pesquisas por campo

	@Test
	void findByTitle() {
		List<Movie> result = movieRepository.findByTitleContainingIgnoreCase("ULTRON");

		assertEquals(1, result.size());
		assertEquals("Avengers: Age of Ultron", result.get(0).getTitle());
	}

	@Test
	void findByDirector() {
		List<Movie> result = movieRepository.findByDirectorContainingIgnoreCase("nolan");

		assertEquals(1, result.size());
		assertEquals("Interstellar", result.get(0).getTitle());
	}

	@Test
	void findByActors() {
		List<Movie> result = movieRepository.findByActorsContainingIgnoreCase("STREEP");

		assertEquals(1, result.size());
		assertEquals(mammaMia.getId(), result.get(0).getId());
	}

//searchByQuery - titulo, realizador e atores

	@Test
	void searchByQueryTitle() {
		List<Movie> result = movieRepository.searchByQuery("mamma");

		assertEquals(1, result.size());
		assertEquals("Mamma Mia!", result.get(0).getTitle());
	}

	@Test
	void searchByQueryDirector() {
		List<Movie> result = movieRepository.searchByQuery("WHEDON");

		assertEquals(1, result.size());
		assertEquals(avengers.getId(), result.get(0).getId());
	}

	@Test
	void searchByQueryActors() {
		List<Movie> result = movieRepository.searchByQuery("schwarzenegger");

		assertEquals(1, result.size());
		assertEquals("Terminator 2: Judgment Day", result.get(0).getTitle());
	}

	@Test
	void searchByQueryWithNoResults() {
		List<Movie> result = movieRepository.searchByQuery("zzzz");

		assertTrue(result.isEmpty());
	}

	@Test
	void searchByQueryWithNoDirectorAndActors() {
		movieRepository.save(new Movie("Sem Equipa", 2010, Set.of(MovieGenre.DRAMA), "synopsis", null, null));

		List<Movie> result = movieRepository.searchByQuery("equipa");

		assertEquals(1, result.size());
		assertEquals("Sem Equipa", result.get(0).getTitle());
	}
}