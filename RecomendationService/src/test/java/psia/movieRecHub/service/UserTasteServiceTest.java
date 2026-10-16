package psia.movieRecHub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import psia.movieRecHub.client.MovieServiceClient;
import psia.movieRecHub.dto.response.ListDetailResponseDto;
import psia.movieRecHub.dto.response.ListDetailResponseDto.ListItem;
import psia.movieRecHub.dto.response.ListSumResponseDto;
import psia.movieRecHub.dto.response.MovieResponseDto;
import psia.movieRecHub.dto.response.ReviewResponseDto;
import psia.movieRecHub.engine.MovieDoc;
import psia.movieRecHub.engine.UserTaste;
import psia.movieRecHub.entities.KnownUser;

@ExtendWith(MockitoExtension.class)
class UserTasteServiceTest {

  private static final long USER = 7L;
  private static final LocalDateTime DAY1 = LocalDateTime.of(2026, 10, 1, 12, 0);

  @Mock MovieServiceClient movieClient;
  @Mock CatalogRefreshService catalogue;
  @Mock KnowUserService knowUserService;

  UserTasteService service;

  @BeforeEach
  void setUp() {
    service = new UserTasteService(movieClient, catalogue, knowUserService);
    // por omissao: sem listas, sem reviews, sem perfil, catalogo vazio
    lenient().when(movieClient.listsOf(USER)).thenReturn(List.of());
    lenient().when(movieClient.reviewsOf(USER)).thenReturn(List.of());
    lenient().when(knowUserService.find(USER)).thenReturn(Optional.empty());
    lenient().when(catalogue.movies()).thenReturn(List.of());
  }

  private static MovieResponseDto movie(long id, String title, String... genres) {
    return new MovieResponseDto(id, title, 2000, List.of(genres), "", 8.0, "", "");
  }

  private void listOf(long listId, String type, ListItem... items) {
    when(movieClient.listsOf(USER)).thenReturn(List.of(new ListSumResponseDto(listId, "x", type)));
    when(movieClient.list(listId, USER))
        .thenReturn(new ListDetailResponseDto(listId, "x", type, List.of(items)));
  }

  @Test
  void userWithNothingHasAnEmptyTaste() {
    UserTaste taste = service.load(USER);

    assertTrue(taste.isEmpty());
    assertTrue(taste.knownMovieIds().isEmpty());
  }

  @Test
  void listsGoIntoTheirOwnSectionsWithGenres() {
    listOf(
        1L,
        "WATCHLIST",
        new ListItem(movie(10, "Heat", "DRAMA", "CRIME"), DAY1),
        new ListItem(movie(11, "Alien", "SCI_FI"), DAY1));

    UserTaste taste = service.load(USER);

    assertEquals("Watchlist: Alien [SCI_FI]; Heat [CRIME,DRAMA].", taste.tasteText());
    assertEquals(Set.of(10L, 11L), taste.knownMovieIds());
  }

  @Test
  void favoritesAndCustomListsAreSeparateSections() {
    when(movieClient.listsOf(USER))
        .thenReturn(
            List.of(
                new ListSumResponseDto(1L, "f", "FAVORITES"),
                new ListSumResponseDto(2L, "c", "CUSTOM")));
    when(movieClient.list(1L, USER))
        .thenReturn(
            new ListDetailResponseDto(
                1L, "f", "FAVORITES", List.of(new ListItem(movie(10, "Heat"), DAY1))));
    when(movieClient.list(2L, USER))
        .thenReturn(
            new ListDetailResponseDto(
                2L, "c", "CUSTOM", List.of(new ListItem(movie(11, "Alien"), DAY1))));

    String text = service.load(USER).tasteText();

    assertTrue(text.contains("Favorites: Heat."));
    assertTrue(text.contains("Other lists: Alien."));
  }

  @Test
  void onlyTheEightMostRecentMoviesAppearButAllCountAsSeen() {
    List<ListItem> items = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      // o filme 1 e o mais antigo, o 10 o mais recente
      items.add(new ListItem(movie(i, "Movie" + (char) ('A' + i)), DAY1.plusDays(i)));
    }
    listOf(1L, "WATCHLIST", items.toArray(new ListItem[0]));

    UserTaste taste = service.load(USER);

    assertFalse(taste.tasteText().contains("MovieB"), "o mais antigo ficou de fora");
    assertFalse(taste.tasteText().contains("MovieC"), "o segundo mais antigo ficou de fora");
    assertTrue(taste.tasteText().contains("MovieK"), "o mais recente esta la");
    assertEquals(10, taste.knownMovieIds().size());
  }

  @Test
  void textDoesNotDependOnTheOrderTheMovieServiceAnswers() {
    listOf(
        1L,
        "WATCHLIST",
        new ListItem(movie(10, "Heat"), DAY1),
        new ListItem(movie(11, "Alien"), DAY1));
    String first = service.load(USER).tasteText();

    listOf(
        1L,
        "WATCHLIST",
        new ListItem(movie(11, "Alien"), DAY1),
        new ListItem(movie(10, "Heat"), DAY1));
    String second = service.load(USER).tasteText();

    assertEquals(first, second);
  }

  @Test
  void highRatingsAreLikedAndLowRatingsAreDisliked() {
    when(catalogue.movies())
        .thenReturn(
            List.of(
                new MovieDoc(1, "Heat", 1995, Set.of("CRIME"), "", 8, "", ""),
                new MovieDoc(2, "Cats", 2019, Set.of("MUSICAL"), "", 3, "", ""),
                new MovieDoc(3, "Up", 2009, Set.of("ANIMATION"), "", 8, "", "")));
    when(movieClient.reviewsOf(USER))
        .thenReturn(
            List.of(
                new ReviewResponseDto(1L, 5, "", DAY1),
                new ReviewResponseDto(2L, 1, "", DAY1),
                new ReviewResponseDto(3L, 3, "", DAY1)));

    UserTaste taste = service.load(USER);

    assertTrue(taste.tasteText().contains("Rated highly: Heat [CRIME]."));
    assertTrue(taste.tasteText().contains("Rated poorly: Cats [MUSICAL]."));
    assertFalse(taste.tasteText().contains("Up"), "nota 3 nao diz nada sobre o gosto");
    assertEquals(Set.of(1L, 2L, 3L), taste.knownMovieIds(), "mas conta como ja visto");
  }

  @Test
  void aReviewedMovieMissingFromTheCatalogueIsStillSeen() {
    when(movieClient.reviewsOf(USER)).thenReturn(List.of(new ReviewResponseDto(99L, 5, "", DAY1)));

    UserTaste taste = service.load(USER);

    assertEquals(Set.of(99L), taste.knownMovieIds());
    assertTrue(taste.isEmpty());
  }

  @Test
  void profileGenresAndDecadesComeFirstAndAreSorted() {
    KnownUser user = new KnownUser(USER);
    user.updateProfile(Set.of("SCI_FI", "DRAMA"), Set.of(1990, 1970));
    when(knowUserService.find(USER)).thenReturn(Optional.of(user));

    UserTaste taste = service.load(USER);

    assertEquals(
        "Favorite genres: DRAMA, SCI_FI. Favorite decades: 1970s, 1990s.", taste.tasteText());
    assertTrue(taste.knownMovieIds().isEmpty(), "o perfil nao conta como filmes vistos");
  }

  @Test
  void brokenListEntriesAreIgnored() {
    listOf(
        1L,
        "WATCHLIST",
        new ListItem(null, DAY1),
        new ListItem(new MovieResponseDto(null, "x", 2000, null, "", 1.0, "", ""), DAY1),
        new ListItem(movie(10, "Heat"), DAY1));

    UserTaste taste = service.load(USER);

    assertEquals("Watchlist: Heat.", taste.tasteText());
    assertEquals(Set.of(10L), taste.knownMovieIds());
  }
}
