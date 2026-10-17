package psia.movieRecHub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import psia.movieRecHub.dto.response.RecommendationResponseItemDto;
import psia.movieRecHub.dto.response.RecommendationResultDto;
import psia.movieRecHub.engine.UserTaste;
import psia.movieRecHub.service.KnowUserService;
import psia.movieRecHub.service.RecommendationCacheService;
import psia.movieRecHub.service.RecommendationService;
import psia.movieRecHub.service.UserTasteService;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean RecommendationService service;
  @MockitoBean RecommendationCacheService cacheService;
  @MockitoBean UserTasteService tasteService;
  @MockitoBean KnowUserService knownUserService;

  private static RecommendationResultDto oneMovie(String source) {
    return new RecommendationResultDto(
        source,
        List.of(new RecommendationResponseItemDto(1, "Heat", 1995, Set.of("CRIME"), "Genres")));
  }

  @Test
  void askUsesTheTasteOfTheUserWhoAsked() throws Exception {
    UserTaste taste = new UserTaste("Favorites: Heat.", Set.of(1L));
    when(tasteService.load(5L)).thenReturn(taste);
    when(service.ask("a comedy", taste)).thenReturn(oneMovie("LLM"));

    mvc.perform(
            post("/api/recommendations")
                .header("X-User-Id", 5)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"a comedy\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("LLM"))
        .andExpect(jsonPath("$.items[0].title").value("Heat"));
  }

  @Test
  void askStillWorksWhenTheTasteCannotBeLoaded() throws Exception {
    when(tasteService.load(5L)).thenThrow(new RuntimeException("MovieService down"));
    when(service.ask(eq("a comedy"), any(UserTaste.class))).thenReturn(oneMovie("LLM"));

    mvc.perform(
            post("/api/recommendations")
                .header("X-User-Id", 5)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"a comedy\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("LLM"));
  }

  @Test
  void askWithoutUserSkipsTheTaste() throws Exception {
    when(service.ask(eq("a comedy"), any(UserTaste.class))).thenReturn(oneMovie("FALLBACK"));

    mvc.perform(
            post("/api/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"a comedy\"}"))
        .andExpect(status().isOk());

    verify(tasteService, never()).load(anyLong());
  }

  @Test
  void askWithBlankTextIsABadRequest() throws Exception {
    mvc.perform(
            post("/api/recommendations")
                .header("X-User-Id", 5)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"   \"}"))
        .andExpect(status().isBadRequest());

    verify(service, never()).ask(any(), any());
  }

  @Test
  void getWithoutCacheAnswersPendingAndRegistersTheUser() throws Exception {
    when(cacheService.find(5L)).thenReturn(Optional.empty());

    mvc.perform(get("/api/recommendations").header("X-User-Id", 5))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("PENDING"))
        .andExpect(jsonPath("$.items").isEmpty());

    verify(knownUserService).register(5L);
  }

  @Test
  void getWithCacheReturnsTheCachedRecommendations() throws Exception {
    when(cacheService.find(5L)).thenReturn(Optional.of(oneMovie("LLM")));

    mvc.perform(get("/api/recommendations").header("X-User-Id", 5))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("LLM"))
        .andExpect(jsonPath("$.items[0].movieId").value(1));
  }

  @Test
  void getWithoutUserHeaderIsABadRequest() throws Exception {
    mvc.perform(get("/api/recommendations")).andExpect(status().isBadRequest());
  }

  @Test
  void putProfileSavesItAndAnswersNoContent() throws Exception {
    mvc.perform(
            put("/api/recommendations/users/5/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"favoriteGenres\":[\"DRAMA\"],\"favoriteDecades\":[1990]}"))
        .andExpect(status().isNoContent());

    verify(knownUserService).saveProfile(5L, Set.of("DRAMA"), Set.of(1990));
  }
}
