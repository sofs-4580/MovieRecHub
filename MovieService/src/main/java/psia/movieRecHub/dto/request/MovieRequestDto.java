package psia.movieRecHub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import psia.movieRecHub.entities.MovieGenre;

public class MovieRequestDto {

  @NotBlank private Long id;
  @NotBlank private String title;
  @NotNull private int releaseYear;
  @NotEmpty private Set<MovieGenre> genres;
  @NotBlank private String synopsis;
  @NotNull private double avgRating;
  private String director;
  private String actors;

  public MovieRequestDto() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public int getReleaseYear() {
    return releaseYear;
  }

  public void setReleaseYear(int releaseYear) {
    this.releaseYear = releaseYear;
  }

  public Set<MovieGenre> getGenres() {
    return genres;
  }

  public void setGenres(Set<MovieGenre> genres) {
    this.genres = genres;
  }

  public String getSynopsis() {
    return synopsis;
  }

  public void setSynopsis(String synopsis) {
    this.synopsis = synopsis;
  }

  public double getAvgRating() {
    return avgRating;
  }

  public void setAvgRating(double avgRating) {
    this.avgRating = avgRating;
  }

  public String getDirector() {
    return director;
  }

  public void setDirector(String director) {
    this.director = director;
  }

  public String getActors() {
    return actors;
  }

  public void setActors(String actors) {
    this.actors = actors;
  }
}
