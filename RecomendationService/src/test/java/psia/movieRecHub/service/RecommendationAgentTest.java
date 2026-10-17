package psia.movieRecHub.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import psia.movieRecHub.engine.Hash;
import psia.movieRecHub.engine.UserTaste;
import psia.movieRecHub.entities.KnownUser;
import psia.movieRecHub.repository.KnownUserRepository;

@ExtendWith(MockitoExtension.class)
class RecommendationAgentTest {

  @Mock KnownUserRepository users;
  @Mock UserTasteService tasteService;
  @Mock RecommendationService recommendationService;
  @Mock RecommendationCacheService cacheService;

  RecommendationAgent agent;

  @BeforeEach
  void setUp() {
    agent = new RecommendationAgent(users, tasteService, recommendationService, cacheService, 3);
  }

  private void knownUsers(Long... ids) {
    when(users.findAll()).thenReturn(Arrays.stream(ids).map(KnownUser::new).toList());
  }

  private void tasteOf(long userId, String text, Long... seen) {
    when(tasteService.load(userId))
        .thenReturn(new UserTaste(text, Set.copyOf(Arrays.asList(seen))));
  }

  @Test
  void generatesForAUserWithoutCache() {
    knownUsers(1L);
    tasteOf(1, "Favorites: Heat.", 4L);
    when(cacheService.findInputHash(1L)).thenReturn(Optional.empty());

    agent.run();

    verify(recommendationService).refreshFor(1L, "Favorites: Heat.", Set.of(4L));
  }

  @Test
  void skipsUsersWhoseTasteDidNotChange() {
    knownUsers(1L);
    tasteOf(1, "Favorites: Heat.");
    when(cacheService.findInputHash(1L)).thenReturn(Optional.of(Hash.sha256("Favorites: Heat.")));

    agent.run();

    verify(recommendationService, never()).refreshFor(anyLong(), any(), any());
  }

  @Test
  void regeneratesWhenTheTasteChanged() {
    knownUsers(1L);
    tasteOf(1, "Favorites: Heat; Alien.");
    when(cacheService.findInputHash(1L)).thenReturn(Optional.of(Hash.sha256("Favorites: Heat.")));

    agent.run();

    verify(recommendationService).refreshFor(eq(1L), eq("Favorites: Heat; Alien."), any());
  }

  @Test
  void skipsUsersWithNoSignals() {
    knownUsers(1L);
    tasteOf(1, "  ");

    agent.run();

    verify(recommendationService, never()).refreshFor(anyLong(), any(), any());
  }

  @Test
  void generatesAtMostMaxPerRunAndLeavesTheRestForTheNextRound() {
    agent = new RecommendationAgent(users, tasteService, recommendationService, cacheService, 2);
    knownUsers(1L, 2L, 3L, 4L);
    tasteOf(1, "t1");
    tasteOf(2, "t2");
    when(cacheService.findInputHash(anyLong())).thenReturn(Optional.empty());

    agent.run();

    verify(recommendationService, times(2)).refreshFor(anyLong(), any(), any());
    verify(recommendationService, never()).refreshFor(eq(3L), any(), any());
  }

  @Test
  void usersThatDoNotCallTheModelDoNotUseUpTheLimit() {
    agent = new RecommendationAgent(users, tasteService, recommendationService, cacheService, 1);
    knownUsers(1L, 2L);
    tasteOf(1, "");
    tasteOf(2, "t2");
    when(cacheService.findInputHash(2L)).thenReturn(Optional.empty());

    agent.run();

    verify(recommendationService).refreshFor(eq(2L), eq("t2"), any());
  }

  @Test
  void aFailingUserDoesNotStopTheOthers() {
    knownUsers(1L, 2L);
    when(tasteService.load(1L)).thenThrow(new RuntimeException("MovieService down"));
    tasteOf(2, "t2");
    when(cacheService.findInputHash(2L)).thenReturn(Optional.empty());

    agent.run();

    verify(recommendationService).refreshFor(eq(2L), eq("t2"), any());
  }
}
