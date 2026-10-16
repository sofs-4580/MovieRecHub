package psia.movieRecHub.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(
    ignoreUnknown = true) // pra ignorar os camps q o movie service tenha e n usemos
public record MovieResponseDto(
    Long id,
    String title,
    Integer releaseYear,
    List<String> genres,
    String synopsis,
    Double avgRating,
    String director,
    String actors) {}
