package psia.movieRecHub.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "movie_list",
    uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "name"}))
public class MovieList {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // id do utilizador no UserService (sem FK: cada microservico tem a sua BD)
  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ListType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ListVisibility visibility;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  private List<MovieListItem> items = new ArrayList<>();

  protected MovieList() {}

  public MovieList(
      Long ownerId, String name, String description, ListType type, ListVisibility visibility) {
    this.ownerId = ownerId;
    this.name = name;
    this.description = description;
    this.type = type;
    this.visibility = visibility;
    this.createdAt = LocalDateTime.now();
  }

  public boolean isOwnedBy(Long userId) {
    return userId != null && userId.equals(ownerId);
  }

  public boolean containsMovie(Long movieId) {
    return items.stream().anyMatch(i -> i.getMovie().getId().equals(movieId));
  }

  /** Acrescenta o filme no fim da lista. */
  public MovieListItem addMovie(Movie movie) {
    MovieListItem item = new MovieListItem(this, movie, items.size() + 1);
    items.add(item);
    return item;
  }

  /** Remove o filme e volta a numerar as posicoes (1..n). Devolve false se nao estava na lista. */
  public boolean removeMovie(Long movieId) {
    boolean removed = items.removeIf(i -> i.getMovie().getId().equals(movieId));
    if (removed) {
      for (int i = 0; i < items.size(); i++) {
        items.get(i).setPosition(i + 1);
      }
    }
    return removed;
  }

  public Long getId() {
    return id;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public ListType getType() {
    return type;
  }

  public ListVisibility getVisibility() {
    return visibility;
  }

  public void setVisibility(ListVisibility visibility) {
    this.visibility = visibility;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public List<MovieListItem> getItems() {
    return items;
  }
}
