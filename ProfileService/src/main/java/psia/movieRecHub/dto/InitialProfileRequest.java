package psia.movieRecHub.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import psia.movieRecHub.model.MovieGenre;

public record InitialProfileRequest(
    @NotEmpty @Size(max = 10) Set<@NotNull MovieGenre> favoriteGenres) {}
