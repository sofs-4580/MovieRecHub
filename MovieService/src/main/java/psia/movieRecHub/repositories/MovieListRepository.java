package psia.movieRecHub.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import psia.movieRecHub.entities.ListType;
import psia.movieRecHub.entities.ListVisibility;
import psia.movieRecHub.entities.MovieList;

@Repository
public interface MovieListRepository extends JpaRepository<MovieList, Long> {

  List<MovieList> findByOwnerIdOrderByCreatedAtAsc(Long ownerId);

  List<MovieList> findByOwnerIdAndVisibilityOrderByCreatedAtAsc(
      Long ownerId, ListVisibility visibility);

  boolean existsByOwnerIdAndName(Long ownerId, String name);

  boolean existsByOwnerIdAndType(Long ownerId, ListType type);
}
