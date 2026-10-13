package psia.movieRecHub.client;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import psia.movieRecHub.dto.MovieResponse;
import psia.movieRecHub.engine.MovieDoc;

@Component
public class MovieServiceClient {

  private final RestTemplate rest;
  private final String baseUrl;

  public MovieServiceClient(
      @Qualifier("movieRestTemplate") RestTemplate rest,
      @Value("${movie.service.url}") String baseUrl) {
    this.rest = rest;
    this.baseUrl = baseUrl;
  }

  /** Vai buscar o catálogo todo ao MovieService. */
  public List<MovieDoc> movies() {
    MovieResponse[] raw = rest.getForObject(baseUrl + "/api/movies", MovieResponse[].class);
    if (raw == null) {
      return List.of();
    }
    return Arrays.stream(raw)
        .filter(m -> m.id() != null && m.title() != null) // ignora entradas estragadas
        .map(MovieServiceClient::toDoc)
        .toList();
  }

  private static MovieDoc toDoc(MovieResponse m) {
    Set<String> genres = new TreeSet<>();
    if (m.genres() != null) {
      m.genres().forEach(g -> genres.add(g.toUpperCase()));
    }
    return new MovieDoc(
        m.id(),
        m.title(),
        m.releaseYear() == null ? 0 : m.releaseYear(),
        genres,
        m.synopsis() == null ? "" : m.synopsis(),
        m.avgRating() == null ? 0 : m.avgRating(),
        m.director() == null ? "" : m.director(),
        m.actors() == null ? "" : m.actors());
  }
}
