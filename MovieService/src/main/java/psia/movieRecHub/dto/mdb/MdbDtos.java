package psia.movieRecHub.dto.mdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class MdbDtos {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MdbGenre(int id, String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MdbGenreListResponse(List<MdbGenre> genres) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MdbMovieSummary(
      long id,
      String title,
      String overview,
      @JsonProperty("release_date") String releaseDate,
      @JsonProperty("genre_ids") List<Integer> genreIds) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MdbMovieListResponse(List<MdbMovieSummary> results) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MdbCastMember(String name, int order) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MdbCrewMember(String name, String job) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MdbCreditsResponse(List<MdbCastMember> cast, List<MdbCrewMember> crew) {}
}
