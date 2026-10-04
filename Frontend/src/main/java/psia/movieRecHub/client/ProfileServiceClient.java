package psia.movieRecHub.client;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import psia.movieRecHub.dto.MovieGenre;
import psia.movieRecHub.dto.OwnProfileDto;
import psia.movieRecHub.dto.PublicProfileDto;
import psia.movieRecHub.dto.PublicProfilePageDto;

/**
 * Calls ProfileService. It authenticates requests by asking UserService about the JSESSIONID
 * cookie, so we forward the browser's session id.
 */
@Component
public class ProfileServiceClient {

  private static final String SERVICE = "The profile service";

  private final ServiceCaller caller;
  private final String baseUrl;

  public ProfileServiceClient(
      ServiceCaller caller, @Value("${profile.service.url}") String baseUrl) {
    this.caller = caller;
    this.baseUrl = baseUrl;
  }

  public PublicProfilePageDto browse(String sessionId, String username, int page, int size) {
    UriComponentsBuilder uri =
        UriComponentsBuilder.fromUriString(baseUrl + "/api/profiles")
            .queryParam("page", page)
            .queryParam("size", size);
    if (username != null && !username.isBlank()) {
      uri.queryParam("username", username);
    }
    return caller
        .exchange(
            SERVICE,
            HttpMethod.GET,
            uri.build().toUriString(),
            headers(sessionId),
            null,
            PublicProfilePageDto.class)
        .getBody();
  }

  public PublicProfileDto get(String sessionId, long userId) {
    return caller
        .exchange(
            SERVICE,
            HttpMethod.GET,
            baseUrl + "/api/profiles/{id}",
            headers(sessionId),
            null,
            PublicProfileDto.class,
            userId)
        .getBody();
  }

  /** Throws ApiException with code PROFILE_NOT_READY while UserService is still creating it. */
  public OwnProfileDto getOwn(String sessionId) {
    return caller
        .exchange(
            SERVICE,
            HttpMethod.GET,
            baseUrl + "/api/profiles/me",
            headers(sessionId),
            null,
            OwnProfileDto.class)
        .getBody();
  }

  public void updateOwn(
      String sessionId,
      String displayName,
      String bio,
      Set<MovieGenre> favoriteGenres,
      Set<Integer> favoriteDecades) {
    List<String> genres = favoriteGenres.stream().map(MovieGenre::name).sorted().toList();
    caller.exchange(
        SERVICE,
        HttpMethod.PATCH,
        baseUrl + "/api/profiles/me",
        headers(sessionId),
        Map.of(
            "displayName", displayName,
            "bio", bio,
            "favoriteGenres", genres,
            "favoriteDecades", favoriteDecades.stream().sorted().toList()),
        Void.class);
  }

  private HttpHeaders headers(String sessionId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    if (sessionId != null) {
      headers.set(HttpHeaders.COOKIE, "JSESSIONID=" + sessionId);
    }
    return headers;
  }
}
