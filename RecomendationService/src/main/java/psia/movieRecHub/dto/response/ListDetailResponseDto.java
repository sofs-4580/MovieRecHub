package psia.movieRecHub.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ListDetailResponseDto(Long id, String name, String type, List<ListItem> items) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ListItem(MovieResponseDto movie) {}
}
