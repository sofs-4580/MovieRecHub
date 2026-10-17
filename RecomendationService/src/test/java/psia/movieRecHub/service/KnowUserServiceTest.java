package psia.movieRecHub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import psia.movieRecHub.entities.KnownUser;
import psia.movieRecHub.repository.KnownUserRepository;

@ExtendWith(MockitoExtension.class)
class KnowUserServiceTest {

  @Mock KnownUserRepository repository;

  KnowUserService service;

  @BeforeEach
  void setUp() {
    service = new KnowUserService(repository);
  }

  @Test
  void registerCreatesTheUserWhenUnknown() {
    when(repository.findById(5L)).thenReturn(Optional.empty());
    when(repository.save(any(KnownUser.class))).thenAnswer(inv -> inv.getArgument(0));

    KnownUser user = service.register(5L);

    assertEquals(5L, user.getUserId());
    verify(repository).save(any(KnownUser.class));
  }

  @Test
  void registerKeepsTheExistingUser() {
    KnownUser existing = new KnownUser(5L);
    when(repository.findById(5L)).thenReturn(Optional.of(existing));

    assertSame(existing, service.register(5L));
    verify(repository, never()).save(any(KnownUser.class));
  }

  @Test
  void saveProfileCleansAndUppercasesGenresAndDropsNulls() {
    KnownUser user = new KnownUser(5L);
    when(repository.findById(5L)).thenReturn(Optional.of(user));
    Set<String> genres = new HashSet<>(Arrays.asList(" drama ", "", "  ", null, "Comedy"));
    Set<Integer> decades = new HashSet<>(Arrays.asList(1990, null));

    boolean changed = service.saveProfile(5L, genres, decades);

    assertTrue(changed);
    assertEquals(Set.of("DRAMA", "COMEDY"), user.getFavoriteGenres());
    assertEquals(Set.of(1990), user.getFavoriteDecades());
    verify(repository).save(user);
  }

  @Test
  void savingTheSameProfileTwiceReportsNoChange() {
    KnownUser user = new KnownUser(5L);
    when(repository.findById(5L)).thenReturn(Optional.of(user));

    assertTrue(service.saveProfile(5L, Set.of("DRAMA"), Set.of(1990)));
    assertFalse(service.saveProfile(5L, Set.of("drama"), Set.of(1990)));
  }

  @Test
  void nullGenresAndDecadesMeanAnEmptyProfile() {
    KnownUser user = new KnownUser(5L);
    user.updateProfile(Set.of("DRAMA"), Set.of(1990));
    when(repository.findById(5L)).thenReturn(Optional.of(user));

    assertTrue(service.saveProfile(5L, null, null));
    assertTrue(user.getFavoriteGenres().isEmpty());
    assertTrue(user.getFavoriteDecades().isEmpty());
  }
}
