package psia.movieRecHub.dto;

import java.util.Set;

public record MovieResponseDto(Long id, String title, int releaseYear, Set<MovieGenre> genres, String synopsis,
		double avgRating, String director, String actors) {
}
