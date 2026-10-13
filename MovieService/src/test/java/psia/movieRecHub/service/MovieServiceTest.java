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
import psia.movieRecHub.dto.response.MovieResponseDto;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.MovieGenre;
import psia.movieRecHub.exceptions.MovieNotFoundException;
import psia.movieRecHub.repositories.MovieRepository;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

	@Mock
	private MovieRepository movieRepository;

	@InjectMocks
	private MovieService movieService;

//getAll

	@Test
	void getAllMovies() {
		Movie mov1 = TestData.movie(1L, "mov1");
		Movie mov2 = TestData.movie(2L, "mov2");
		when(movieRepository.findAll()).thenReturn(List.of(mov1, mov2));

		List<MovieResponseDto> result = movieService.getAllMovies(null);

		assertEquals(2, result.size());
		assertEquals("mov1", result.get(0).title());
		assertEquals("mov2", result.get(1).title());
	}

	@Test
	void getAllMoviesByGenre() {
		Movie mov2 = TestData.movie(2L, "mov2");
		when(movieRepository.findByGenresContaining(MovieGenre.HORROR)).thenReturn(List.of(mov2));

		List<MovieResponseDto> result = movieService.getAllMovies(MovieGenre.HORROR);

		assertEquals(1, result.size());
		assertEquals(2L, result.get(0).id());
		// com genero nao vai buscar todos os filmes
		verify(movieRepository, never()).findAll();
	}

	@Test
	void getAllMoviesWithoutMovies() {
		when(movieRepository.findAll()).thenReturn(List.of());

		List<MovieResponseDto> result = movieService.getAllMovies(null);

		assertTrue(result.isEmpty());
	}

//getById

	@Test
	void getMovieById() {
		Movie mov1 = TestData.movie(1L, "mov1");
		mov1.setAvgRating(4.5);
		when(movieRepository.findById(1L)).thenReturn(Optional.of(mov1));

		MovieResponseDto result = movieService.getMovieById(1L);

		assertEquals(1L, result.id());
		assertEquals("mov1", result.title());
		assertEquals(mov1.getReleaseYear(), result.releaseYear());
		assertEquals(mov1.getGenres(), result.genres());
		assertEquals(mov1.getSynopsis(), result.synopsis());
		assertEquals(4.5, result.avgRating());
		assertEquals(mov1.getDirector(), result.director());
		assertEquals(mov1.getActors(), result.actors());
	}

	@Test
	void getMovieByIdThatDoesNotExist() {
		when(movieRepository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(MovieNotFoundException.class, () -> movieService.getMovieById(99L));
	}

//pesquisas 
	@Test
	void search() {
		Movie mov1 = TestData.movie(1L, "mov1");
		when(movieRepository.searchByQuery("mov")).thenReturn(List.of(mov1));

		List<MovieResponseDto> result = movieService.search("mov");

		assertEquals(1, result.size());
		assertEquals(1L, result.get(0).id());
	}

	@Test
	void searchWithNoResults() {
		when(movieRepository.searchByQuery("zzz")).thenReturn(List.of());

		List<MovieResponseDto> result = movieService.search("zzz");

		assertTrue(result.isEmpty());
	}

	@Test
	void searchByTitle() {
		Movie mov1 = TestData.movie(1L, "mov1");
		when(movieRepository.findByTitleContainingIgnoreCase("mov")).thenReturn(List.of(mov1));

		List<MovieResponseDto> result = movieService.searchByTitle("mov");

		assertEquals(1, result.size());
		assertEquals("mov1", result.get(0).title());
	}

	@Test
	void searchByDirector() {
		Movie mov1 = TestData.movie(1L, "mov1");
		when(movieRepository.findByDirectorContainingIgnoreCase("director")).thenReturn(List.of(mov1));

		List<MovieResponseDto> result = movieService.searchByDirector("director");

		assertEquals(1, result.size());
		assertEquals(1L, result.get(0).id());
	}

	@Test
	void searchByActor() {
		Movie mov1 = TestData.movie(1L, "mov1");
		when(movieRepository.findByActorsContainingIgnoreCase("actor")).thenReturn(List.of(mov1));

		List<MovieResponseDto> result = movieService.searchByActor("actor");

		assertEquals(1, result.size());
		assertEquals(1L, result.get(0).id());
	}

	@Test
	void getByReleaseYear() {
		Movie mov1 = TestData.movie(1L, "mov1");
		when(movieRepository.findByReleaseYear(1999)).thenReturn(List.of(mov1));

		List<MovieResponseDto> result = movieService.getByReleaseYear(1999);

		assertEquals(1, result.size());
		assertEquals(1L, result.get(0).id());
	}

//recalculateAvgRating

	@Test
	void recalculateAvgRating() {
		Movie mov1 = TestData.movie(1L, "mov1");
		when(movieRepository.findById(1L)).thenReturn(Optional.of(mov1));

		movieService.recalculateAvgRating(1L, 3.7);

		assertEquals(3.7, mov1.getAvgRating());
		verify(movieRepository).save(mov1);
	}

	@Test
	void avgRatingOfNonExistingMovie() {
		when(movieRepository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(MovieNotFoundException.class, () -> movieService.recalculateAvgRating(99L, 3.0));

		verify(movieRepository, never()).save(any());
	}
}