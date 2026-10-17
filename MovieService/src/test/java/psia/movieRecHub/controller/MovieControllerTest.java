package psia.movieRecHub.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import psia.movieRecHub.dto.response.MovieResponseDto;
import psia.movieRecHub.entities.MovieGenre;
import psia.movieRecHub.exceptions.MovieNotFoundException;
import psia.movieRecHub.service.MovieService;

// new MovieResponseDto(id, title, releaseYear, genres, synopsis, avgRating, director, actors)
@WebMvcTest(MovieController.class)
class MovieControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MovieService movieService;

  // GET /api/movies

  @Test
  void getAllMovies() throws Exception {
    MovieResponseDto terminator =
        new MovieResponseDto(
            1L,
            "Terminator 2: Judgment Day",
            1991,
            Set.of(MovieGenre.SCI_FI),
            "synopsis",
            4.5,
            "James Cameron",
            "Arnold Schwarzenegger");
    MovieResponseDto interstellar =
        new MovieResponseDto(
            2L,
            "Interstellar",
            2014,
            Set.of(MovieGenre.DRAMA),
            "synopsis",
            4.0,
            "Christopher Nolan",
            "Matthew McConaughey");
    when(movieService.getAllMovies(null)).thenReturn(List.of(terminator, interstellar));

    mockMvc
        .perform(get("/api/movies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].title").value("Terminator 2: Judgment Day"))
        .andExpect(jsonPath("$[0].releaseYear").value(1991))
        .andExpect(jsonPath("$[0].genres[0]").value("SCI_FI"))
        .andExpect(jsonPath("$[0].avgRating").value(4.5))
        .andExpect(jsonPath("$[0].director").value("James Cameron"))
        .andExpect(jsonPath("$[1].title").value("Interstellar"));
  }

  @Test
  void getAllMoviesByGenre() throws Exception {
    MovieResponseDto terminator =
        new MovieResponseDto(
            1L,
            "Terminator 2: Judgment Day",
            1991,
            Set.of(MovieGenre.SCI_FI),
            "synopsis",
            4.5,
            "James Cameron",
            "Arnold Schwarzenegger");
    when(movieService.getAllMovies(MovieGenre.SCI_FI)).thenReturn(List.of(terminator));

    mockMvc
        .perform(get("/api/movies").param("genre", "SCI_FI"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(1));
  }

  @Test
  void getAllMoviesWithoutMovies() throws Exception {
    when(movieService.getAllMovies(null)).thenReturn(List.of());

    mockMvc
        .perform(get("/api/movies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // GET /api/movies/{id}

  @Test
  void getMovieById() throws Exception {
    MovieResponseDto terminator =
        new MovieResponseDto(
            1L,
            "Terminator 2: Judgment Day",
            1991,
            Set.of(MovieGenre.SCI_FI),
            "synopsis",
            4.5,
            "James Cameron",
            "Arnold Schwarzenegger");
    when(movieService.getMovieById(1L)).thenReturn(terminator);

    mockMvc
        .perform(get("/api/movies/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.title").value("Terminator 2: Judgment Day"))
        .andExpect(jsonPath("$.synopsis").value("synopsis"))
        .andExpect(jsonPath("$.actors").value("Arnold Schwarzenegger"));
  }

  @Test
  void getMovieByIdThatDoesNotExist() throws Exception {
    when(movieService.getMovieById(99L)).thenThrow(new MovieNotFoundException(99L));

    mockMvc
        .perform(get("/api/movies/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Movie not found"))
        .andExpect(jsonPath("$.errorCode").value("MOVIE_ERR_001"));
  }

  // pesquisas

  @Test
  void search() throws Exception {
    MovieResponseDto terminator =
        new MovieResponseDto(
            1L,
            "Terminator 2: Judgment Day",
            1991,
            Set.of(MovieGenre.SCI_FI),
            "synopsis",
            4.5,
            "James Cameron",
            "Arnold Schwarzenegger");
    when(movieService.search("arnold")).thenReturn(List.of(terminator));

    mockMvc
        .perform(get("/api/movies/search").param("query", "arnold"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(1));
  }

  @Test
  void searchWithNoResults() throws Exception {
    when(movieService.search("zzz")).thenReturn(List.of());

    mockMvc
        .perform(get("/api/movies/search").param("query", "zzz"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void searchByTitle() throws Exception {
    MovieResponseDto terminator =
        new MovieResponseDto(
            1L,
            "Terminator 2: Judgment Day",
            1991,
            Set.of(MovieGenre.SCI_FI),
            "synopsis",
            4.5,
            "James Cameron",
            "Arnold Schwarzenegger");
    when(movieService.searchByTitle("terminator")).thenReturn(List.of(terminator));

    mockMvc
        .perform(get("/api/movies/search/title").param("title", "terminator"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1));
  }

  @Test
  void searchByDirector() throws Exception {
    MovieResponseDto terminator =
        new MovieResponseDto(
            1L,
            "Terminator 2: Judgment Day",
            1991,
            Set.of(MovieGenre.SCI_FI),
            "synopsis",
            4.5,
            "James Cameron",
            "Arnold Schwarzenegger");
    when(movieService.searchByDirector("cameron")).thenReturn(List.of(terminator));

    mockMvc
        .perform(get("/api/movies/search/director").param("director", "cameron"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1));
  }

  @Test
  void searchByActor() throws Exception {
    MovieResponseDto terminator =
        new MovieResponseDto(
            1L,
            "Terminator 2: Judgment Day",
            1991,
            Set.of(MovieGenre.SCI_FI),
            "synopsis",
            4.5,
            "James Cameron",
            "Arnold Schwarzenegger");
    when(movieService.searchByActor("arnold")).thenReturn(List.of(terminator));

    mockMvc
        .perform(get("/api/movies/search/actor").param("actor", "arnold"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1));
  }

  @Test
  void getByReleaseYear() throws Exception {
    MovieResponseDto terminator =
        new MovieResponseDto(
            1L,
            "Terminator 2: Judgment Day",
            1991,
            Set.of(MovieGenre.SCI_FI),
            "synopsis",
            4.5,
            "James Cameron",
            "Arnold Schwarzenegger");
    when(movieService.getByReleaseYear(1991)).thenReturn(List.of(terminator));

    mockMvc
        .perform(get("/api/movies/year/1991"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].releaseYear").value(1991));
  }

  // PATCH /api/movies/{id}/rating

  @Test
  void recalculateAvgRating() throws Exception {
    mockMvc
        .perform(patch("/api/movies/1/rating").param("newAvgRating", "4.2"))
        .andExpect(status().isNoContent());

    verify(movieService).recalculateAvgRating(1L, 4.2);
  }

  @Test
  void recalculateAvgRatingNonExistingMovie() throws Exception {
    doThrow(new MovieNotFoundException(99L))
        .when(movieService)
        .recalculateAvgRating(anyLong(), anyDouble());

    mockMvc
        .perform(patch("/api/movies/99/rating").param("newAvgRating", "4.2"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("MOVIE_ERR_001"));
  }

  // pedidos invalidos (400)

  @Test
  void getAllMoviesWithInvalidGenre() throws Exception {
    mockMvc
        .perform(get("/api/movies").param("genre", "NAO_EXISTE"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void searchWithoutQuery() throws Exception {
    mockMvc.perform(get("/api/movies/search")).andExpect(status().isBadRequest());
  }
}
