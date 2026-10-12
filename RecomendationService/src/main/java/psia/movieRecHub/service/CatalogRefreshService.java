package psia.movieRecHub.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import psia.movieRecHub.client.MovieServiceClient;
import psia.movieRecHub.engine.MovieDoc;

@Service
public class CatalogRefreshService {

  private static final Logger log = LoggerFactory.getLogger(CatalogRefreshService.class);

  private final MovieServiceClient client;
  private volatile List<MovieDoc> movies = List.of();

  public CatalogRefreshService(MovieServiceClient client) {
    this.client = client;
  }

  // os filmes q temos no momento
  public List<MovieDoc> movies() {
    return movies;
  }

  @Scheduled(
      initialDelayString = "${catalogue.refresh.initial-delay-ms}",
      fixedDelayString = "${catalogue.refresh.interval-ms}")
  public void refresh() {
    try {
      List<MovieDoc> fresh = client.movies();
      if (fresh.isEmpty()) {
        log.warn("MovieService returned no movies, keeping the {} we have", movies.size());
        return;
      }
      movies = List.copyOf(fresh);
      log.info("Catalogue refreshed: {} movies", fresh.size());
    } catch (RestClientException e) {
      log.warn("MovieService unavailable, keeping {} movies: {}", movies.size(), e.getMessage());
    }
  }
}
