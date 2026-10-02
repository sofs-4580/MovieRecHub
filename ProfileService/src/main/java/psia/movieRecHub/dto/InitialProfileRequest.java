package psia.movieRecHub.dto;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import psia.movieRecHub.model.MovieGenre;

public record InitialProfileRequest(
    @Pattern(regexp = "[a-z0-9_]{3,30}") String username,
    @NotNull @Size(max = 10) Set<@NotNull MovieGenre> favoriteGenres) {}
