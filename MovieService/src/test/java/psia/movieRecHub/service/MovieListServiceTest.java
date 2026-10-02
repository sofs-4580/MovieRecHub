package psia.movieRecHub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import psia.movieRecHub.TestData;
import psia.movieRecHub.dto.request.ListRequestDto;
import psia.movieRecHub.dto.request.ListUpdateDto;
import psia.movieRecHub.dto.response.MovieListDetailDto;
import psia.movieRecHub.dto.response.MovieListSummaryDto;
import psia.movieRecHub.entities.ListType;
import psia.movieRecHub.entities.ListVisibility;
import psia.movieRecHub.entities.MovieList;
import psia.movieRecHub.exceptions.DuplicateListException;
import psia.movieRecHub.exceptions.ListAccessDeniedException;
import psia.movieRecHub.exceptions.ListItemNotFoundException;
import psia.movieRecHub.exceptions.ListNotFoundException;
import psia.movieRecHub.exceptions.MovieNotFoundException;
import psia.movieRecHub.repositories.MovieListRepository;
import psia.movieRecHub.repositories.MovieRepository;

@ExtendWith(MockitoExtension.class)
class MovieListServiceTest {

	@Mock
	private MovieListRepository listRepository;

	@Mock
	private MovieRepository movieRepository;

	@InjectMocks
	private MovieListService listService;

	// when: define o valor que o repository mock tem de devolver quando o método
	// for chamado (thenReturn ...)

//create list

	@Test
	void createList() {
		MovieList list = TestData.list(1L, 2L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.existsByOwnerIdAndName(2L, "lista")).thenReturn(false);
		when(listRepository.save(any(MovieList.class))).thenReturn(list);

		MovieListDetailDto result = listService.createList(2L,
				new ListRequestDto("lista", "desc", ListVisibility.PRIVATE));

		assertEquals(1L, result.id());
		assertEquals(2L, result.ownerId());
		assertEquals("lista", result.name());
		assertEquals(ListType.CUSTOM, result.type());
		assertEquals(ListVisibility.PRIVATE, result.visibility());
		assertTrue(result.items().isEmpty());
	}

	@Test
	void createListRepeatedName() {
		when(listRepository.existsByOwnerIdAndName(1L, "lista")).thenReturn(true);

		assertThrows(DuplicateListException.class,
				() -> listService.createList(1L, new ListRequestDto("lista", "desc", ListVisibility.PUBLIC)));

		verify(listRepository, never()).save(any());
	}

//listsOfUsers
	@Test
	void ownerHasDefaultLists() {
		when(listRepository.existsByOwnerIdAndType(1L, ListType.WATCHLIST)).thenReturn(false);
		when(listRepository.existsByOwnerIdAndType(1L, ListType.FAVORITES)).thenReturn(false);
		when(listRepository.findByOwnerIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

		listService.getListsOfUser(1L, 1L);

		verify(listRepository, times(2)).save(any(MovieList.class));
	}

	@Test
	void defaultListsCreatedOnce() {
		when(listRepository.existsByOwnerIdAndType(1L, ListType.WATCHLIST)).thenReturn(true);
		when(listRepository.existsByOwnerIdAndType(1L, ListType.FAVORITES)).thenReturn(true);
		when(listRepository.findByOwnerIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

		listService.getListsOfUser(1L, 1L);

		verify(listRepository, never()).save(any());
	}

	@Test
	void ownerSeesHisLists() {
		MovieList privateList = TestData.list(10L, 1L, "privada", ListType.CUSTOM, ListVisibility.PRIVATE);
		MovieList publicList = TestData.list(11L, 1L, "publica", ListType.CUSTOM, ListVisibility.PUBLIC);
		privateList.addMovie(TestData.movie(5L, "mov1"));
		privateList.addMovie(TestData.movie(6L, "mov2"));

		when(listRepository.existsByOwnerIdAndType(1L, ListType.WATCHLIST)).thenReturn(true);
		when(listRepository.existsByOwnerIdAndType(1L, ListType.FAVORITES)).thenReturn(true);
		when(listRepository.findByOwnerIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(privateList, publicList));

		List<MovieListSummaryDto> result = listService.getListsOfUser(1L, 1L);

		assertEquals("privada", result.get(0).name());
		assertEquals("publica", result.get(1).name());

		assertEquals(2, result.size());
		assertEquals(2, result.get(0).movieCount());
		assertEquals(0, result.get(1).movieCount());
	}

	@Test
	void otherUserSeesOnlyPublic() {
		MovieList publicList = TestData.list(11L, 1L, "publica", ListType.CUSTOM, ListVisibility.PUBLIC);
		when(listRepository.findByOwnerIdAndVisibilityOrderByCreatedAtAsc(1L, ListVisibility.PUBLIC))
				.thenReturn(List.of(publicList));

		List<MovieListSummaryDto> result = listService.getListsOfUser(1L, 2L);

		assertEquals(1, result.size());
		assertEquals("publica", result.get(0).name());
		verify(listRepository, never()).save(any());
	}

//getList

	@Test
	void getPublicList() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PUBLIC);
		list.addMovie(TestData.movie(5L, "mov1"));
		list.addMovie(TestData.movie(6L, "mov2"));
		
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		MovieListDetailDto result = listService.getList(10L, 2L);

		assertEquals(10L, result.id());
		assertEquals(2, result.items().size());
		assertEquals(1, result.items().get(0).position());
		assertEquals(2, result.items().get(1).position());
		assertEquals("mov1", result.items().get(0).movie().title());
		assertEquals("mov2", result.items().get(1).movie().title());
	}

	@Test
	void getPrivateListOwner() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		MovieListDetailDto result = listService.getList(10L, 1L);
		assertEquals(10L, result.id());
	}

	@Test
	void getPrivateListOtherUser() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));
		assertThrows(ListNotFoundException.class, () -> listService.getList(10L, 2L));
	}

	@Test
	void getListDoesntExist() {
		when(listRepository.findById(10L)).thenReturn(Optional.empty());
		assertThrows(ListNotFoundException.class, () -> listService.getList(10L, 1L));
	}

//updateList

	@Test
	void updateList() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));
		when(listRepository.existsByOwnerIdAndName(1L, "nova")).thenReturn(false);

		listService.updateList(10L, 1L, new ListUpdateDto("nova", "nova desc", ListVisibility.PUBLIC));

		assertEquals("nova", list.getName());
		assertEquals("nova desc", list.getDescription());
		assertEquals(ListVisibility.PUBLIC, list.getVisibility());
	}

	@Test
	void updateListNullFields() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		listService.updateList(10L, 1L, new ListUpdateDto(null, null, null));

		assertEquals("lista", list.getName());
		assertEquals("desc", list.getDescription());
		assertEquals(ListVisibility.PRIVATE, list.getVisibility());
	}

	@Test
	void updateListRepeatedName() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));
		when(listRepository.existsByOwnerIdAndName(1L, "outra")).thenReturn(true);

		assertThrows(DuplicateListException.class,
				() -> listService.updateList(10L, 1L, new ListUpdateDto("outra", null, null)));
		assertEquals("lista", list.getName());
	}

	@Test
	void updateListClearDescription() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		listService.updateList(10L, 1L, new ListUpdateDto(null, "", null));
		assertEquals("", list.getDescription());
	}

	@Test
	void updateListTryClearVisibility() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		listService.updateList(10L, 1L, new ListUpdateDto(null, null, null));
		assertEquals(ListVisibility.PRIVATE, list.getVisibility());
	}

	@Test
	void updateListTryClearName() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		assertThrows(IllegalArgumentException.class,
				() -> listService.updateList(10L, 1L, new ListUpdateDto("   ", null, null)));
		assertEquals("lista", list.getName());
	}

	@Test
	void defaultListRenamed() {
		MovieList list = TestData.list(10L, 1L, "Watchlist", ListType.WATCHLIST, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		assertThrows(IllegalArgumentException.class,
				() -> listService.updateList(10L, 1L, new ListUpdateDto("nova", null, null)));
		assertEquals("Watchlist", list.getName());
	}

	@Test
	void updatePublicListOtherUser() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PUBLIC);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		assertThrows(ListAccessDeniedException.class,
				() -> listService.updateList(10L, 2L, new ListUpdateDto("nova", null, null)));
		assertEquals("lista", list.getName());
	}

	@Test
	void updatePrivateListOtherUser() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		// lista privada - e como se n existisse
		assertThrows(ListNotFoundException.class,
				() -> listService.updateList(10L, 2L, new ListUpdateDto("nova", null, null)));
	}

//delete

	@Test
	void deleteList() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		listService.deleteList(10L, 1L);
		verify(listRepository).delete(list);
	}

	@Test
	void defaultListDeleted() {
		MovieList list = TestData.list(10L, 1L, "Watchlist", ListType.WATCHLIST, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		assertThrows(IllegalArgumentException.class, () -> listService.deleteList(10L, 1L));
		verify(listRepository, never()).delete(any());
	}

	@Test
	void deleteListAsOtherUser() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PUBLIC);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		assertThrows(ListAccessDeniedException.class, () -> listService.deleteList(10L, 2L));
		verify(listRepository, never()).delete(any());
	}

// add movie

	@Test
	void addMovie() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		list.addMovie(TestData.movie(5L, "mov1"));
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));
		when(movieRepository.findById(6L)).thenReturn(Optional.of(TestData.movie(6L, "mov2")));

		MovieListDetailDto result = listService.addMovie(10L, 6L, 1L);

		assertEquals(2, result.items().size());
		assertEquals(6L, result.items().get(1).movie().id());
		assertEquals(2, result.items().get(1).position());
	}

	@Test
	void addMovieThatDoentExist() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));
		when(movieRepository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(MovieNotFoundException.class, () -> listService.addMovie(10L, 99L, 1L));
		assertTrue(list.getItems().isEmpty());
	}

	@Test
	void addMovieAlreadyInList() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		list.addMovie(TestData.movie(5L, "mov1"));
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));
		when(movieRepository.findById(5L)).thenReturn(Optional.of(TestData.movie(5L, "mov1")));

		assertThrows(DuplicateListException.class, () -> listService.addMovie(10L, 5L, 1L));
		assertEquals(1, list.getItems().size());
	}

	@Test
	void addMovieAsOtherUser() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PUBLIC);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		assertThrows(ListAccessDeniedException.class, () -> listService.addMovie(10L, 5L, 2L));
		assertTrue(list.getItems().isEmpty());
	}

//remove movie

	@Test
	void removeMovie() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		list.addMovie(TestData.movie(5L, "mov1"));
		list.addMovie(TestData.movie(6L, "mov2"));
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		listService.removeMovie(10L, 5L, 1L);

		assertEquals(1, list.getItems().size());
		assertEquals(6L, list.getItems().get(0).getMovie().getId());
		assertEquals(1, list.getItems().get(0).getPosition());
	}

	@Test
	void removeMovieNotInList() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PRIVATE);
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		assertThrows(ListItemNotFoundException.class, () -> listService.removeMovie(10L, 5L, 1L));
	}

	@Test
	void removeMovieOtherUser() {
		MovieList list = TestData.list(10L, 1L, "lista", ListType.CUSTOM, ListVisibility.PUBLIC);
		list.addMovie(TestData.movie(5L, "mov1"));
		when(listRepository.findById(10L)).thenReturn(Optional.of(list));

		assertThrows(ListAccessDeniedException.class, () -> listService.removeMovie(10L, 5L, 2L));
		assertEquals(1, list.getItems().size());
	}
}