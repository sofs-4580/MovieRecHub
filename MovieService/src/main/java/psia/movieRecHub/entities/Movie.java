package psia.movieRecHub.entities;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Movie {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private int releaseYear;

  @ElementCollection(targetClass = MovieGenre.class, fetch = FetchType.EAGER)
  @Enumerated(EnumType.STRING)
  private Set<MovieGenre> genres = new HashSet<>();

  @Column(length = 2000)
  private String synopsis;

  @Column(nullable = false)
  private double avgRating = 0.0;

  protected Movie() {}

  public Movie(String title, int releaseYear, Set<MovieGenre> genres, String synopsis) {
    this.title = title;
    this.releaseYear = releaseYear;
    this.genres = genres;
    this.synopsis = synopsis;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public int getReleaseYear() {
    return releaseYear;
  }

  public Set<MovieGenre> getGenres() {
    return genres;
  }

  public String getSynopsis() {
    return synopsis;
  }

  public double getAvgRating() {
    return avgRating;
  }

  public void setAvgRating(double avgRating) {
    this.avgRating = avgRating;
  }
}
