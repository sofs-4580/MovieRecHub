package psia.movieRecHub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import psia.movieRecHub.client.MovieServiceClient;
import psia.movieRecHub.engine.MovieDoc;

@ExtendWith(MockitoExtension.class)
class CatalogRefreshServiceTest {

  @Mock MovieServiceClient client;

  CatalogRefreshService service;

  private static MovieDoc movie(long id) {
    return new MovieDoc(id, "M" + id, 2000, Set.of("DRAMA"), "", 8.0, "", "");
  }

  @BeforeEach
  void setUp() {
    service = new CatalogRefreshService(client);
  }

  @Test
  void startsEmptyBeforeTheFirstRefresh() {
    assertTrue(service.movies().isEmpty());
  }

  @Test
  void refreshLoadsTheCatalogue() {
    when(client.movies()).thenReturn(List.of(movie(1), movie(2)));

    service.refresh();

    assertEquals(2, service.movies().size());
  }

  @Test
  void keepsTheOldCatalogueWhenMovieServiceReturnsNothing() {
    when(client.movies()).thenReturn(List.of(movie(1), movie(2))).thenReturn(List.of());

    service.refresh();
    service.refresh();

    assertEquals(2, service.movies().size());
  }

  @Test
  void keepsTheOldCatalogueWhenMovieServiceIsDown() {
    when(client.movies())
        .thenReturn(List.of(movie(1)))
        .thenThrow(new ResourceAccessException("connection refused"));

    service.refresh();
    service.refresh();

    assertEquals(1, service.movies().size());
  }
}
