package psia.movieRecHub.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import psia.movieRecHub.entities.MovieEmbedding;

public interface MovieEmbeddingRepository extends JpaRepository<MovieEmbedding, Long> {

  /** All vectors computed with the given model. */
  List<MovieEmbedding> findByModel(String model);
}
