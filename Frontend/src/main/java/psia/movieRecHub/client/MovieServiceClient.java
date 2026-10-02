package psia.movieRecHub.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import psia.movieRecHub.dto.ListRequestDto;
import psia.movieRecHub.dto.ListVisibility;
import psia.movieRecHub.dto.MovieGenre;
import psia.movieRecHub.dto.MovieListDetailDto;
import psia.movieRecHub.dto.MovieListSummaryDto;
import psia.movieRecHub.dto.MovieResponseDto;
import psia.movieRecHub.dto.ReviewRequestDto;
import psia.movieRecHub.dto.ReviewResponseDto;

/**
 * quem faz o pedido vai no header X-User-Id (TODO: passa a vir da sessao quando
 * houver login).
 */
@Component
public class MovieServiceClient {

	private static final String USER_HEADER = "X-User-Id";

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;
	private final String baseUrl;

	public MovieServiceClient(RestTemplate restTemplate, ObjectMapper objectMapper,
			@Value("${movie.service.url}") String baseUrl) {
		this.restTemplate = restTemplate;
		this.objectMapper = objectMapper;
		this.baseUrl = baseUrl;
	}

	// ---------- filmes ----------

	public List<MovieResponseDto> getMovies(MovieGenre genre) {
		if (genre == null) {
			return getList("/api/movies", null, new ParameterizedTypeReference<List<MovieResponseDto>>() {
			});
		}
		return getList("/api/movies?genre={genre}", null, new ParameterizedTypeReference<List<MovieResponseDto>>() {
		}, genre);
	}

	public MovieResponseDto getMovie(Long id) {
		return call(HttpMethod.GET, "/api/movies/{id}", null, null, MovieResponseDto.class, id);
	}

	// ---------- reviews ----------

	public List<ReviewResponseDto> getReviewsByMovie(Long movieId) {
		return getList("/api/reviews/movie/{movieId}", null, new ParameterizedTypeReference<List<ReviewResponseDto>>() {
		}, movieId);
	}

	public ReviewResponseDto createReview(Long userId, Long movieId, int rating, String comment) {
		ReviewRequestDto body = new ReviewRequestDto(userId, movieId, rating, comment);
		return call(HttpMethod.POST, "/api/reviews", null, body, ReviewResponseDto.class);
	}

	// ---------- listas ----------

	/** requesterId == null: pedido anonimo, so ve as listas publicas. */
	public List<MovieListSummaryDto> getListsOfUser(Long ownerId, Long requesterId) {
		return getList("/api/lists/user/{ownerId}", requesterId,
				new ParameterizedTypeReference<List<MovieListSummaryDto>>() {
				}, ownerId);
	}

	public MovieListDetailDto getList(Long listId, Long requesterId) {
		return call(HttpMethod.GET, "/api/lists/{id}", requesterId, null, MovieListDetailDto.class, listId);
	}

	public MovieListDetailDto createList(Long userId, String name, String description, ListVisibility visibility) {
		ListRequestDto body = new ListRequestDto(name, description, visibility);
		return call(HttpMethod.POST, "/api/lists", userId, body, MovieListDetailDto.class);
	}

	public void deleteList(Long listId, Long userId) {
		call(HttpMethod.DELETE, "/api/lists/{id}", userId, null, Void.class, listId);
	}

	public MovieListDetailDto addMovieToList(Long listId, Long movieId, Long userId) {
		return call(HttpMethod.POST, "/api/lists/{id}/movies/{movieId}", userId, null, MovieListDetailDto.class, listId,
				movieId);
	}

	public void removeMovieFromList(Long listId, Long movieId, Long userId) {
		call(HttpMethod.DELETE, "/api/lists/{id}/movies/{movieId}", userId, null, Void.class, listId, movieId);
	}

	// ---------- helpers ----------

	private <T> T call(HttpMethod method, String path, Long userId, Object body, Class<T> type, Object... uriVars) {
		try {
			return restTemplate.exchange(baseUrl + path, method, entity(userId, body), type, uriVars).getBody();
		} catch (RestClientResponseException ex) {
			throw new MovieServiceException(errorMessage(ex));
		} catch (ResourceAccessException ex) {
			throw new MovieServiceException("Movie service is not available");
		}
	}

	/** GET de listas */
	private <T> List<T> getList(String path, Long userId, ParameterizedTypeReference<List<T>> type, Object... uriVars) {
		try {
			return restTemplate.exchange(baseUrl + path, HttpMethod.GET, entity(userId, null), type, uriVars).getBody();
		} catch (RestClientResponseException ex) {
			throw new MovieServiceException(errorMessage(ex));
		} catch (ResourceAccessException ex) {
			throw new MovieServiceException("Movie service is not available");
		}
	}

	private HttpEntity<Object> entity(Long userId, Object body) {
		HttpHeaders headers = new HttpHeaders();
		if (userId != null) {
			headers.set(USER_HEADER, String.valueOf(userId));
		}
		return new HttpEntity<>(body, headers);
	}

	/**
	 * As respostas de erro do MovieService sao JSON com "message" (texto
	 * especifico) e "detail" (texto generico). Usa a primeira que existir.
	 */
	private String errorMessage(RestClientResponseException ex) {
		try {
			JsonNode json = objectMapper.readTree(ex.getResponseBodyAsString());
			if (json.hasNonNull("message")) {
				return json.get("message").asText();
			}
			if (json.hasNonNull("detail")) {
				return json.get("detail").asText();
			}
		} catch (Exception ignored) {
			// corpo vazio ou que nao e JSON: fica a mensagem generica
		}
		return "Request to movie service failed (" + ex.getStatusCode().value() + ")";
	}
}
