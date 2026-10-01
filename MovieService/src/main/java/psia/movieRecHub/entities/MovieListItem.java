package psia.movieRecHub.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "movie_list_item",
    uniqueConstraints = @UniqueConstraint(columnNames = {"list_id", "movie_id"}))
public class MovieListItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "list_id", nullable = false)
  private MovieList list;

  @ManyToOne(optional = false)
  @JoinColumn(name = "movie_id", nullable = false)
  private Movie movie;

  @Column(nullable = false)
  private int position;

  @Column(nullable = false)
  private LocalDateTime addedAt;

  protected MovieListItem() {}

  MovieListItem(MovieList list, Movie movie, int position) {
    this.list = list;
    this.movie = movie;
    this.position = position;
    this.addedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public MovieList getList() {
    return list;
  }

  public Movie getMovie() {
    return movie;
  }

  public int getPosition() {
    return position;
  }

  void setPosition(int position) {
    this.position = position;
  }

  public LocalDateTime getAddedAt() {
    return addedAt;
  }
}
