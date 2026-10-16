package psia.movieRecHub.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import psia.movieRecHub.client.MovieServiceClient;
import psia.movieRecHub.dto.response.ListDetailResponseDto;
import psia.movieRecHub.dto.response.ListSumResponseDto;
import psia.movieRecHub.dto.response.MovieResponseDto;
import psia.movieRecHub.dto.response.ReviewResponseDto;
import psia.movieRecHub.engine.MovieDoc;
import psia.movieRecHub.engine.UserTaste;
import psia.movieRecHub.entities.KnownUser;

@Service
public class UserTasteService {

  private static final int MAX_PER_SECTION = 8;

  // filme no texto do gosto, com a data em que o utilizador o adicionou ou avaliou
  private record Entry(String label, LocalDateTime when) {}

  private final MovieServiceClient movieClient;
  private final CatalogRefreshService catalogue;
  private final KnowUserService knowUserService;

  public UserTasteService(
      MovieServiceClient movieClient,
      CatalogRefreshService catalogue,
      KnowUserService knowUserService) {
    this.movieClient = movieClient;
    this.catalogue = catalogue;
    this.knowUserService = knowUserService;
  }

  public UserTaste load(long userId) {
    Set<Long> known = new TreeSet<>();
    List<Entry> favorites = new ArrayList<>();
    List<Entry> watchlist = new ArrayList<>();
    List<Entry> otherLists = new ArrayList<>();
    List<Entry> liked = new ArrayList<>();
    List<Entry> disliked = new ArrayList<>();

    // filmes nas listas (favoritos, watchlist, outras)
    for (ListSumResponseDto summary : movieClient.listsOf(userId)) {
      ListDetailResponseDto detail = movieClient.list(summary.id(), userId);
      if (detail == null || detail.items() == null) continue;
      for (ListDetailResponseDto.ListItem item : detail.items()) {
        MovieResponseDto m = item.movie();
        if (m == null || m.id() == null || m.title() == null) continue;
        known.add(m.id());
        Entry entry = new Entry(label(m.title(), m.genres()), item.addedAt());
        switch (String.valueOf(detail.type())) {
          case "FAVORITES" -> favorites.add(entry);
          case "WATCHLIST" -> watchlist.add(entry);
          default -> otherLists.add(entry);
        }
      }
    }

    // reviews, nota alta = gostou, nota baixa = n gostou
    Map<Long, MovieDoc> byId =
        catalogue.movies().stream().collect(Collectors.toMap(MovieDoc::id, Function.identity()));
    for (ReviewResponseDto r : movieClient.reviewsOf(userId)) {
      if (r.movieId() == null || r.rating() == null) continue;
      known.add(r.movieId());
      MovieDoc m = byId.get(r.movieId());
      if (m == null) continue;
      Entry entry = new Entry(label(m.title(), m.genres()), r.createdAt());
      if (r.rating() >= 4) liked.add(entry);
      else if (r.rating() <= 2) disliked.add(entry);
    }

    StringBuilder sb = new StringBuilder();
    knowUserService.find(userId).ifPresent(u -> profileSection(sb, u));
    section(sb, "Favorites", favorites);
    section(sb, "Watchlist", watchlist);
    section(sb, "Other lists", otherLists);
    section(sb, "Rated highly", liked);
    section(sb, "Rated poorly", disliked);
    return new UserTaste(sb.toString().trim(), known);
  }

  // generos e decadas de filmes escolhidos quando criou perfil
  private static void profileSection(StringBuilder sb, KnownUser user) {
    if (!user.getFavoriteGenres().isEmpty()) {
      sb.append("Favorite genres: ")
          .append(String.join(", ", new TreeSet<>(user.getFavoriteGenres())))
          .append(". ");
    }
    if (!user.getFavoriteDecades().isEmpty()) {
      String decades =
          new TreeSet<>(user.getFavoriteDecades())
              .stream().map(d -> d + "s").collect(Collectors.joining(", "));
      sb.append("Favorite decades: ").append(decades).append(". ");
    }
  }

  private static String label(String title, Collection<String> genres) {
    String g =
        (genres == null || genres.isEmpty())
            ? ""
            : " [" + genres.stream().sorted().collect(Collectors.joining(",")) + "]";
    return title + g;
  }

  // max per section = 8 filmes mais recentes (o gosto atual conta mais)
  // depois ordena alfabeticamente, para o texto (e o hash) n depender da ordem em que o movie
  // service responde
  private static void section(StringBuilder sb, String name, List<Entry> entries) {
    if (entries.isEmpty()) return;
    List<String> top =
        entries.stream()
            .sorted(
                Comparator.comparing(Entry::when, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(Entry::label)
            .distinct()
            .limit(MAX_PER_SECTION)
            .sorted()
            .toList();
    sb.append(name).append(": ").append(String.join("; ", top)).append(". ");
  }
}
