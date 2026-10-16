package psia.movieRecHub.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewResponseDto(
    Long movieId, Integer rating, String comment, LocalDateTime createdAt) {}
