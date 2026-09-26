package psia.movieRecHub.service;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import psia.movieRecHub.dto.response.MovieResponseDto;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.MovieGenre;
import psia.movieRecHub.exceptions.MovieNotFoundException;
import psia.movieRecHub.repositories.MovieRepository;

@Service
public class MovieService {

  @Autowired private MovieRepository movieRepository;

  public List<MovieResponseDto> getAllMovies(MovieGenre genre) {
    List<Movie> movies =
        (genre != null) ? movieRepository.findByGenresContaining(genre) : movieRepository.findAll();
    return movies.stream().map(this::mapMovieToMovieResponse).toList();
  }

  public MovieResponseDto getMovieById(Long id) {
    Optional<Movie> movieOpt = movieRepository.findById(id);
    if (movieOpt.isEmpty()) {
      throw new MovieNotFoundException(id);
    }
    return mapMovieToMovieResponse(movieOpt.get());
  }

  public List<MovieResponseDto> searchByTitle(String title) {
    return movieRepository.findByTitleContainingIgnoreCase(title).stream()
        .map(this::mapMovieToMovieResponse)
        .toList();
  }

  public List<MovieResponseDto> search(String query) {
    return movieRepository.searchByQuery(query).stream()
        .map(this::mapMovieToMovieResponse)
        .toList();
  }

  public List<MovieResponseDto> searchByDirector(String director) {
    return movieRepository.findByDirectorContainingIgnoreCase(director).stream()
        .map(this::mapMovieToMovieResponse)
        .toList();
  }

  public List<MovieResponseDto> searchByActor(String actor) {
    return movieRepository.findByActorsContainingIgnoreCase(actor).stream()
        .map(this::mapMovieToMovieResponse)
        .toList();
  }

  public List<MovieResponseDto> getByReleaseYear(int releaseYear) {
    return movieRepository.findByReleaseYear(releaseYear).stream()
        .map(this::mapMovieToMovieResponse)
        .toList();
  }

  public void recalculateAvgRating(Long movieId, double newAvgRating) {
    Optional<Movie> movieOpt = movieRepository.findById(movieId);
    if (movieOpt.isEmpty()) {
      throw new MovieNotFoundException(movieId);
    }
    Movie movie = movieOpt.get();
    movie.setAvgRating(newAvgRating);
    movieRepository.save(movie);
  }

  private MovieResponseDto mapMovieToMovieResponse(Movie movie) {
    return new MovieResponseDto(
        movie.getId(),
        movie.getTitle(),
        movie.getReleaseYear(),
        movie.getGenres(),
        movie.getSynopsis(),
        movie.getAvgRating(),
        movie.getDirector(),
        movie.getActors());
  }
}
