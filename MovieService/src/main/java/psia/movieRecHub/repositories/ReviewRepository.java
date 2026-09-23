package psia.movieRecHub.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import psia.movieRecHub.entities.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByMovieId(Long movieId);
    List<Review> findByUserId(Long userId);

    List<Review> findByRating(int rating);
    
    List<Review> findByMovieIdAndRating(Long movieId, int rating);

    Optional<Review> findByUserIdAndMovieId(Long userId, Long movieId);

    @Query("""
    		SELECT r 
    		FROM Review r 
    		WHERE r.movie.id = :movieId 
    		ORDER BY r.createdAt DESC """)
    List<Review> findReviewsByMovieOrderedByMostRecent(@Param("movieId") Long movieId);
}