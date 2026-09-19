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
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "movie_id"}))
public class Review {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private int rating;

  @Column(nullable = false)
  private String comment;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne
  @JoinColumn(name = "movie_id", nullable = false)
  private Movie movie;

  protected Review() {}

  public Review(int rating, String comment, Long userId, Movie movie) {
    this.rating = rating;
    this.comment = comment;
    this.userId = userId;
    this.movie = movie;
    this.createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public int getRating() {
    return rating;
  }

  public void setRating(int rating) {
    this.rating = rating;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public Long getUserId() {
    return userId;
  }

  public Movie getMovie() {
    return movie;
  }
}
