package psia.movieRecHub.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import psia.movieRecHub.model.MovieGenre;
import psia.movieRecHub.service.InvalidProfileUpdateException;

/** Parsed partial update. Presence flags distinguish omitted fields from explicit nulls. */
public record UpdateProfileRequest(
    boolean displayNamePresent, String displayName,
    boolean bioPresent, String bio,
    boolean favoriteGenresPresent, Set<MovieGenre> favoriteGenres,
    boolean favoriteDecadesPresent, Set<Integer> favoriteDecades) {

  private static final Set<String> ALLOWED = Set.of(
      "displayName", "bio", "favoriteGenres", "favoriteDecades");

  public static UpdateProfileRequest fromJson(JsonNode body) {
    if (body == null || !body.isObject() || body.isEmpty()) {
      throw new InvalidProfileUpdateException();
    }
    Iterator<String> fields = body.fieldNames();
    while (fields.hasNext()) {
      if (!ALLOWED.contains(fields.next())) {
        throw new InvalidProfileUpdateException();
      }
    }
    return new UpdateProfileRequest(
        body.has("displayName"), textOrEmpty(body.get("displayName"), 100),
        body.has("bio"), textOrEmpty(body.get("bio"), 2000),
        body.has("favoriteGenres"), genres(body.get("favoriteGenres")),
        body.has("favoriteDecades"), decades(body.get("favoriteDecades")));
  }

  private static String textOrEmpty(JsonNode value, int maxLength) {
    if (value == null || value.isNull()) {
      return "";
    }
    if (!value.isTextual() || value.textValue().length() > maxLength) {
      throw new InvalidProfileUpdateException();
    }
    return value.textValue();
  }

  private static Set<MovieGenre> genres(JsonNode value) {
    if (value == null) {
      return Set.of();
    }
    if (!value.isArray() || value.isEmpty() || value.size() > 10) {
      throw new InvalidProfileUpdateException();
    }
    Set<MovieGenre> result = new HashSet<>();
    for (JsonNode item : value) {
      if (!item.isTextual()) {
        throw new InvalidProfileUpdateException();
      }
      try {
        result.add(MovieGenre.valueOf(item.textValue()));
      } catch (IllegalArgumentException exception) {
        throw new InvalidProfileUpdateException();
      }
    }
    return Set.copyOf(result);
  }

  private static Set<Integer> decades(JsonNode value) {
    if (value == null || value.isNull()) {
      return Set.of();
    }
    if (!value.isArray() || value.size() > 22) {
      throw new InvalidProfileUpdateException();
    }
    Set<Integer> result = new HashSet<>();
    for (JsonNode item : value) {
      if (!item.isIntegralNumber() || !item.canConvertToInt()
          || item.intValue() < 1880 || item.intValue() > 2090
          || item.intValue() % 10 != 0) {
        throw new InvalidProfileUpdateException();
      }
      result.add(item.intValue());
    }
    return Set.copyOf(result);
  }
}
