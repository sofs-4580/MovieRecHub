package psia.movieRecHub.client;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import psia.movieRecHub.dto.response.ListDetailResponseDto;
import psia.movieRecHub.dto.response.ListSumResponseDto;
import psia.movieRecHub.dto.response.MovieResponseDto;
import psia.movieRecHub.dto.response.ReviewResponseDto;
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

  // Vai buscar o catalog todo ao MovieService
  public List<MovieDoc> movies() {
    MovieResponseDto[] raw = rest.getForObject(baseUrl + "/api/movies", MovieResponseDto[].class);
    if (raw == null) {
      return List.of();
    }
    return Arrays.stream(raw)
        .filter(m -> m.id() != null && m.title() != null) // ignora entradas estragadas
        .map(MovieServiceClient::toDoc)
        .toList();
  }

  private static MovieDoc toDoc(MovieResponseDto m) {
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

  // Listas do utilizador (watchlist, favoritos, custom)
  public List<ListSumResponseDto> listsOf(long userId) {
    ListSumResponseDto[] raw =
        getAs("/api/lists/user/" + userId, userId, ListSumResponseDto[].class);
    return raw == null ? List.of() : Arrays.asList(raw);
  }

  // Uma lista com os filmes la dentro
  public ListDetailResponseDto list(long listId, long ownerId) {
    return getAs("/api/lists/" + listId, ownerId, ListDetailResponseDto.class);
  }

  // Reviews do user
  public List<ReviewResponseDto> reviewsOf(long userId) {
    ReviewResponseDto[] raw =
        rest.getForObject(baseUrl + "/api/reviews/user/" + userId, ReviewResponseDto[].class);
    return raw == null ? List.of() : Arrays.asList(raw);
  }

  // GET com o header X-User-Id, o MovieService sabe quem está a pedir
  private <T> T getAs(String path, long userId, Class<T> type) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-User-Id", String.valueOf(userId));
    return rest.exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<>(headers), type).getBody();
  }
}
