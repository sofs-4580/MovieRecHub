package psia.movieRecHub.dto.response;

import java.util.Set;
import psia.movieRecHub.entities.MovieGenre;

  public record MovieResponseDto(
    Long id,
    String title,
    int releaseYear,
    Set<MovieGenre> genres,
    String synopsis,
    double avgRating
  ) {}
