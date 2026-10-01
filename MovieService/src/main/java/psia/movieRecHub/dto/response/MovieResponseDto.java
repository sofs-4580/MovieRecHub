package psia.movieRecHub.dto.response;

import java.util.Set;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.MovieGenre;

public record MovieResponseDto(
    Long id,
    String title,
    int releaseYear,
    Set<MovieGenre> genres,
    String synopsis,
    double avgRating,
    String director,
    String actors) {

  public static MovieResponseDto from(Movie movie) {
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
