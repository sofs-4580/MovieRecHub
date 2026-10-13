package psia.movieRecHub.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.MovieGenre;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

  List<Movie> findByReleaseYear(int releaseYear);

  List<Movie> findByTitleContainingIgnoreCase(String title);

  List<Movie> findByGenresContaining(MovieGenre genre);

  List<Movie> findByDirectorContainingIgnoreCase(String director);

  List<Movie> findByActorsContainingIgnoreCase(String actor);

  List<Movie> findByAvgRating(double avgRating);

  @Query(
      "SELECT m FROM Movie m WHERE "
          + "LOWER(m.title) LIKE LOWER(CONCAT('%', :query, '%')) OR "
          + "LOWER(m.director) LIKE LOWER(CONCAT('%', :query, '%')) OR "
          + "LOWER(m.actors) LIKE LOWER(CONCAT('%', :query, '%'))")
  List<Movie> searchByQuery(@Param("query") String query);
}
