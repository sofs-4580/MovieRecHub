package psia.movieRecHub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import psia.movieRecHub.dto.request.ListRequestDto;
import psia.movieRecHub.dto.request.ListUpdateDto;
import psia.movieRecHub.dto.response.ListItemResponseDto;
import psia.movieRecHub.dto.response.MovieListDetailDto;
import psia.movieRecHub.dto.response.MovieListSummaryDto;
import psia.movieRecHub.dto.response.MovieResponseDto;
import psia.movieRecHub.entities.ListType;
import psia.movieRecHub.entities.ListVisibility;
import psia.movieRecHub.entities.MovieGenre;
import psia.movieRecHub.exceptions.DuplicateListException;
import psia.movieRecHub.exceptions.ListAccessDeniedException;
import psia.movieRecHub.exceptions.ListItemNotFoundException;
import psia.movieRecHub.exceptions.ListNotFoundException;
import psia.movieRecHub.exceptions.MovieNotFoundException;
import psia.movieRecHub.service.MovieListService;


// new MovieListDetailDto(id, ownerId, name, description, type, visibility, createdAt, items)
@WebMvcTest(MovieListController.class)
class MovieListControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private MovieListService listService;

//POST /api/lists

	@Test
	void createList() throws Exception {
		MovieListDetailDto created = new MovieListDetailDto(10L, 1L, "lista", "desc", ListType.CUSTOM,
				ListVisibility.PUBLIC, LocalDateTime.of(2026, 1, 15, 21, 30), List.of());
		when(listService.createList(eq(1L), any(ListRequestDto.class))).thenReturn(created);

		mockMvc.perform(post("/api/lists").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"lista\", \"description\": \"desc\", \"visibility\": \"PUBLIC\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(10))
				.andExpect(jsonPath("$.ownerId").value(1)).andExpect(jsonPath("$.name").value("lista"))
				.andExpect(jsonPath("$.type").value("CUSTOM")).andExpect(jsonPath("$.visibility").value("PUBLIC"))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void createListNoUserHeader() throws Exception {
		mockMvc.perform(post("/api/lists").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"lista\", \"description\": \"desc\", \"visibility\": \"PUBLIC\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Missing header"));

		// nem chega ao service
		verifyNoInteractions(listService);
	}

	@Test
	void createListBlankName() throws Exception {
		mockMvc.perform(post("/api/lists").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"   \", \"description\": \"desc\", \"visibility\": \"PUBLIC\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Validation failed"));

		verifyNoInteractions(listService);
	}

	@Test
	void createListNameTooLong() throws Exception {
		String name = "x".repeat(101);

		mockMvc.perform(post("/api/lists").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"" + name + "\", \"visibility\": \"PUBLIC\"}"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(listService);
	}

	@Test
	void createListWithoutVisibility() throws Exception {
		mockMvc.perform(post("/api/lists").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"lista\", \"description\": \"desc\"}")).andExpect(status().isBadRequest());

		verifyNoInteractions(listService);
	}

	@Test
	void createListRepeatedName() throws Exception {
		when(listService.createList(eq(1L), any(ListRequestDto.class)))
				.thenThrow(DuplicateListException.name(1L, "lista"));

		mockMvc.perform(post("/api/lists").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"lista\", \"description\": \"desc\", \"visibility\": \"PUBLIC\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("LIST_ERR_003"));
	}

//GET /api/lists/user/{userId}

	@Test
	void getListsOfUser() throws Exception {
		MovieListSummaryDto summary = new MovieListSummaryDto(10L, 1L, "lista", "desc", ListType.CUSTOM,
				ListVisibility.PRIVATE, LocalDateTime.of(2026, 1, 15, 21, 30), 3);
		when(listService.getListsOfUser(1L, 1L)).thenReturn(List.of(summary));

		mockMvc.perform(get("/api/lists/user/1").header("X-User-Id", 1)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("lista"))
				.andExpect(jsonPath("$[0].movieCount").value(3));
	}

	@Test
	void getListsOfUserWithoutHeader() throws Exception {
		// nos GET o header e opcional - sem ele, service recebe null
		when(listService.getListsOfUser(1L, null)).thenReturn(List.of());

		mockMvc.perform(get("/api/lists/user/1")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

		verify(listService).getListsOfUser(1L, null);
	}

//GET /api/lists/{id}

	@Test
	void getList() throws Exception {
		MovieResponseDto terminator = new MovieResponseDto(5L, "Terminator 2: Judgment Day", 1991,
				Set.of(MovieGenre.SCI_FI), "synopsis", 4.5, "James Cameron", "Arnold Schwarzenegger");
		ListItemResponseDto item = new ListItemResponseDto(1, LocalDateTime.of(2026, 1, 15, 21, 30), terminator);
		
		MovieListDetailDto list = new MovieListDetailDto(10L, 1L, "lista", "desc", ListType.CUSTOM,
				ListVisibility.PUBLIC, LocalDateTime.of(2026, 1, 15, 21, 30), List.of(item));
		
		when(listService.getList(10L, 2L)).thenReturn(list);

		mockMvc.perform(get("/api/lists/10").header("X-User-Id", 2)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(10)).andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].position").value(1))
				.andExpect(jsonPath("$.items[0].movie.title").value("Terminator 2: Judgment Day"));
	}

	@Test
	void getListDoesntExist() throws Exception {
		when(listService.getList(10L, null)).thenThrow(new ListNotFoundException(10L));

		mockMvc.perform(get("/api/lists/10")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("LIST_ERR_001"));
	}

//PATCH /api/lists/{id}

	@Test
	void updateList() throws Exception {
		MovieListDetailDto updated = new MovieListDetailDto(10L, 1L, "nova", "desc", ListType.CUSTOM,
				ListVisibility.PRIVATE, LocalDateTime.of(2026, 1, 15, 21, 30), List.of());
		when(listService.updateList(eq(10L), eq(1L), any(ListUpdateDto.class))).thenReturn(updated);

		mockMvc.perform(patch("/api/lists/10").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"nova\", \"visibility\": \"PRIVATE\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("nova")).andExpect(jsonPath("$.visibility").value("PRIVATE"));

		verify(listService).updateList(10L, 1L, new ListUpdateDto("nova", null, ListVisibility.PRIVATE));
	}

	@Test
	void updateListWithoutUserHeader() throws Exception {
		mockMvc.perform(patch("/api/lists/10").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"nova\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Missing header"));

		verifyNoInteractions(listService);
	}

	@Test
	void updateListDescriptionTooLong() throws Exception {
		String description = "d".repeat(501);

		mockMvc.perform(patch("/api/lists/10").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
				.content("{\"description\": \"" + description + "\"}")).andExpect(status().isBadRequest());

		verifyNoInteractions(listService);
	}

	@Test
	void updateListAsOtherUser() throws Exception {
		when(listService.updateList(eq(10L), eq(2L), any(ListUpdateDto.class)))
				.thenThrow(new ListAccessDeniedException(10L));

		mockMvc.perform(patch("/api/lists/10").header("X-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"nova\"}")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("LIST_ERR_002"));
	}

	@Test
	void updateListWithInvalidChange() throws Exception {
		// e o que o service lanca ao renomear uma lista por defeito ou ao por um nome
		// em branco
		when(listService.updateList(eq(10L), eq(1L), any(ListUpdateDto.class)))
				.thenThrow(new IllegalArgumentException("Default lists cannot be renamed"));

		mockMvc.perform(patch("/api/lists/10").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"nova\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Invalid request"))
				.andExpect(jsonPath("$.message").value("Default lists cannot be renamed"));
	}

//DELETE /api/lists/{id}

	@Test
	void deleteList() throws Exception {
		mockMvc.perform(delete("/api/lists/10").header("X-User-Id", 1)).andExpect(status().isNoContent());

		verify(listService).deleteList(10L, 1L);
	}

	@Test
	void deleteListWithoutUserHeader() throws Exception {
		mockMvc.perform(delete("/api/lists/10")).andExpect(status().isBadRequest());

		verifyNoInteractions(listService);
	}

	@Test
	void deleteDefaultList() throws Exception {
		doThrow(new IllegalArgumentException("Default lists cannot be deleted")).when(listService).deleteList(10L, 1L);

		mockMvc.perform(delete("/api/lists/10").header("X-User-Id", 1)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Default lists cannot be deleted"));
	}

//filmes da lista

	@Test
	void addMovie() throws Exception {
		MovieResponseDto terminator = new MovieResponseDto(5L, "Terminator 2: Judgment Day", 1991,
				Set.of(MovieGenre.SCI_FI), "synopsis", 4.5, "James Cameron", "Arnold Schwarzenegger");
		ListItemResponseDto item = new ListItemResponseDto(1, LocalDateTime.of(2026, 1, 15, 21, 30), terminator);
		MovieListDetailDto list = new MovieListDetailDto(10L, 1L, "lista", "desc", ListType.CUSTOM,
				ListVisibility.PUBLIC, LocalDateTime.of(2026, 1, 15, 21, 30), List.of(item));
		when(listService.addMovie(10L, 5L, 1L)).thenReturn(list);

		mockMvc.perform(post("/api/lists/10/movies/5").header("X-User-Id", 1)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].movie.id").value(5));
	}

	@Test
	void addMovieThatDoesNotExist() throws Exception {
		when(listService.addMovie(10L, 99L, 1L)).thenThrow(new MovieNotFoundException(99L));

		mockMvc.perform(post("/api/lists/10/movies/99").header("X-User-Id", 1)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("MOVIE_ERR_001"));
	}

	@Test
	void addMovieAlreadyInList() throws Exception {
		when(listService.addMovie(10L, 5L, 1L)).thenThrow(DuplicateListException.movie(10L, 5L));

		mockMvc.perform(post("/api/lists/10/movies/5").header("X-User-Id", 1)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("LIST_ERR_003"));
	}

	@Test
	void addMovieWithoutUserHeader() throws Exception {
		mockMvc.perform(post("/api/lists/10/movies/5")).andExpect(status().isBadRequest());

		verifyNoInteractions(listService);
	}

	@Test
	void removeMovie() throws Exception {
		mockMvc.perform(delete("/api/lists/10/movies/5").header("X-User-Id", 1)).andExpect(status().isNoContent());

		verify(listService).removeMovie(10L, 5L, 1L);
	}

	@Test
	void removeMovieNotInList() throws Exception {
		doThrow(new ListItemNotFoundException(10L, 5L)).when(listService).removeMovie(10L, 5L, 1L);

		mockMvc.perform(delete("/api/lists/10/movies/5").header("X-User-Id", 1)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("LIST_ERR_001"));
	}

//pedidos invalidos - 400

	@Test
	void deleteListWithInvalidUserHeader() throws Exception {
		mockMvc.perform(delete("/api/lists/10").header("X-User-Id", "abc")).andExpect(status().isBadRequest());
	}
}