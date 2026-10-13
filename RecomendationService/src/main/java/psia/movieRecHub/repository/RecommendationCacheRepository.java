package psia.movieRecHub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import psia.movieRecHub.entities.RecommendationCache;

@Repository
public interface RecommendationCacheRepository extends JpaRepository<RecommendationCache, Long> {}
