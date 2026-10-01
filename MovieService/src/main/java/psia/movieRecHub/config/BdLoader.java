package psia.movieRecHub.config;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import psia.movieRecHub.dto.mdb.MdbDtos.MdbCreditsResponse;
import psia.movieRecHub.dto.mdb.MdbDtos.MdbGenre;
import psia.movieRecHub.dto.mdb.MdbDtos.MdbGenreListResponse;
import psia.movieRecHub.dto.mdb.MdbDtos.MdbMovieListResponse;
import psia.movieRecHub.dto.mdb.MdbDtos.MdbMovieSummary;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.MovieGenre;
import psia.movieRecHub.repositories.MovieRepository;

@Configuration
public class BdLoader {

  private static final String BASE_URL = "https://api.themoviedb.org/3";
  private static final int PAGES_TO_FETCH = 2; // 20 filmes por pagina, ~40 no total

  @Value("${tmdb.api.key}")
  private String apiKey;

  @Bean
  CommandLineRunner seedDatabase(MovieRepository movieRepository, RestTemplate restTemplate) {
    return args -> {
      if (movieRepository.count() > 0) {
        System.out.println(
            "\u001b[33m[DataLoader] Base de dados ja populada, a saltar seed.\u001b[0m");
        return;
      }

      System.out.println(
          "\u001b[32m[DataLoader] A popular base de dados a partir da TMDB...\u001b[0m");

      try {
        Map<Integer, MovieGenre> genreMap = fetchGenreMap(restTemplate);

        for (int page = 1; page <= PAGES_TO_FETCH; page++) {
          MdbMovieListResponse response = fetchTopRatedPage(restTemplate, page);
          for (MdbMovieSummary summary : response.results()) {
            saveMovieFromTmdb(movieRepository, restTemplate, summary, genreMap);
          }
        }

        System.out.println("\u001b[32m[DataLoader] Script pra popular bd concluido!\u001b[0m");
        System.out.println(
            "\u001b[32m[DataLoader] Filmes carregados: " + movieRepository.count() + "\u001b[0m");
      } catch (Exception e) {
        System.out.println(
            "\u001b[31m[DataLoader] Falha ao popular a partir da TMDB: "
                + e.getMessage()
                + "\u001b[0m");
      }
    };
  }

  private Map<Integer, MovieGenre> fetchGenreMap(RestTemplate restTemplate) {
    String url = BASE_URL + "/genre/movie/list?api_key=" + apiKey + "&language=en-US";
    MdbGenreListResponse response = restTemplate.getForObject(url, MdbGenreListResponse.class);

    Map<Integer, MovieGenre> map = new HashMap<>();
    for (MdbGenre genre : response.genres()) {
      mapMdbGenreName(genre.name()).ifPresent(g -> map.put(genre.id(), g));
    }
    return map;
  }

  // a TMDB tem mais generos do que o nosso enum (Adventure, Crime, Family, History,
  // Music, Mystery, War, Western, TV Movie) - esses ficam sem mapeamento de proposito
  private Optional<MovieGenre> mapMdbGenreName(String mdbName) {
    return switch (mdbName) {
      case "Action" -> Optional.of(MovieGenre.ACTION);
      case "Comedy" -> Optional.of(MovieGenre.COMEDY);
      case "Drama" -> Optional.of(MovieGenre.DRAMA);
      case "Horror" -> Optional.of(MovieGenre.HORROR);
      case "Romance" -> Optional.of(MovieGenre.ROMANCE);
      case "Science Fiction" -> Optional.of(MovieGenre.SCI_FI);
      case "Thriller" -> Optional.of(MovieGenre.THRILLER);
      case "Animation" -> Optional.of(MovieGenre.ANIMATION);
      case "Documentary" -> Optional.of(MovieGenre.DOCUMENTARY);
      case "Fantasy" -> Optional.of(MovieGenre.FANTASY);
      default -> Optional.empty();
    };
  }

  private MdbMovieListResponse fetchTopRatedPage(RestTemplate restTemplate, int page) {
    String url = BASE_URL + "/movie/top_rated?api_key=" + apiKey + "&language=en-US&page=" + page;
    return restTemplate.getForObject(url, MdbMovieListResponse.class);
  }

  private void saveMovieFromTmdb(
      MovieRepository movieRepository,
      RestTemplate restTemplate,
      MdbMovieSummary summary,
      Map<Integer, MovieGenre> genreMap) {
    Set<MovieGenre> genres = new HashSet<>();
    for (Integer genreId : summary.genreIds()) {
      MovieGenre mapped = genreMap.get(genreId);
      if (mapped != null) {
        genres.add(mapped);
      }
    }

    if (genres.isEmpty()) {
      return; // nenhum genero mapeado, salta este filme
    }

    int releaseYear = parseYear(summary.releaseDate());
    MdbCreditsResponse credits = fetchCredits(restTemplate, summary.id());

    String director =
        credits.crew().stream()
            .filter(c -> "Director".equals(c.job()))
            .map(c -> c.name())
            .findFirst()
            .orElse("Unknown");

    String actors =
        credits.cast().stream()
            .sorted(Comparator.comparingInt(c -> c.order()))
            .limit(3)
            .map(c -> c.name())
            .reduce((a, b) -> a + ", " + b)
            .orElse("Unknown");

    movieRepository.save(
        new Movie(summary.title(), releaseYear, genres, summary.overview(), director, actors));
  }

  private MdbCreditsResponse fetchCredits(RestTemplate restTemplate, long movieId) {
    String url = BASE_URL + "/movie/" + movieId + "/credits?api_key=" + apiKey;
    return restTemplate.getForObject(url, MdbCreditsResponse.class);
  }

  private int parseYear(String releaseDate) {
    if (releaseDate == null || releaseDate.length() < 4) {
      return 0;
    }
    return Integer.parseInt(releaseDate.substring(0, 4));
  }
}
