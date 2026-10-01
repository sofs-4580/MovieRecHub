package psia.movieRecHub.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.dto.request.ListRequestDto;
import psia.movieRecHub.dto.request.ListUpdateDto;
import psia.movieRecHub.dto.response.ListItemResponseDto;
import psia.movieRecHub.dto.response.MovieListDetailDto;
import psia.movieRecHub.dto.response.MovieListSummaryDto;
import psia.movieRecHub.dto.response.MovieResponseDto;
import psia.movieRecHub.entities.ListType;
import psia.movieRecHub.entities.ListVisibility;
import psia.movieRecHub.entities.Movie;
import psia.movieRecHub.entities.MovieList;
import psia.movieRecHub.exceptions.DuplicateListException;
import psia.movieRecHub.exceptions.ListAccessDeniedException;
import psia.movieRecHub.exceptions.ListItemNotFoundException;
import psia.movieRecHub.exceptions.ListNotFoundException;
import psia.movieRecHub.exceptions.MovieNotFoundException;
import psia.movieRecHub.repositories.MovieListRepository;
import psia.movieRecHub.repositories.MovieRepository;

/**
 * Regras: - So o dono altera uma lista (requesterId tem de ser o ownerId). - Listas PRIVATE nao
 * existem para quem nao e o dono (404, para nao revelar que existem). - WATCHLIST e FAVORITES sao
 * criadas automaticamente, nao se podem apagar nem renomear.
 *
 * <p>requesterId vem para ja de um header X-User-Id; quando houver auth, passa a vir do token.
 */
@Service
@Transactional
public class MovieListService {

  @Autowired private MovieListRepository listRepository;
  @Autowired private MovieRepository movieRepository;

  public MovieListDetailDto createList(Long requesterId, ListRequestDto dto) {
    ensureNameAvailable(requesterId, dto.name());
    MovieList list =
        new MovieList(
            requesterId, dto.name().trim(), dto.description(), ListType.CUSTOM, dto.visibility());
    return toDetail(listRepository.save(list));
  }

  /**
   * Dono ve todas (e as listas por defeito sao criadas se ainda nao existirem); os outros so veem
   * as publicas.
   */
  public List<MovieListSummaryDto> getListsOfUser(Long ownerId, Long requesterId) {
    if (ownerId.equals(requesterId)) {
      ensureDefaultLists(ownerId);
      return listRepository.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream()
          .map(this::toSummary)
          .toList();
    }
    return listRepository
        .findByOwnerIdAndVisibilityOrderByCreatedAtAsc(ownerId, ListVisibility.PUBLIC)
        .stream()
        .map(this::toSummary)
        .toList();
  }

  @Transactional(readOnly = true)
  public MovieListDetailDto getList(Long listId, Long requesterId) {
    MovieList list = findVisible(listId, requesterId);
    return toDetail(list);
  }

  public MovieListDetailDto updateList(Long listId, Long requesterId, ListUpdateDto dto) {
    MovieList list = findOwned(listId, requesterId);

    if (dto.name() != null && !dto.name().trim().equals(list.getName())) {
      if (list.getType() != ListType.CUSTOM) {
        throw new IllegalArgumentException("Default lists cannot be renamed");
      }
      ensureNameAvailable(requesterId, dto.name().trim());
      list.setName(dto.name().trim());
    }
    if (dto.description() != null) {
      list.setDescription(dto.description());
    }
    if (dto.visibility() != null) {
      list.setVisibility(dto.visibility());
    }
    return toDetail(list);
  }

  public void deleteList(Long listId, Long requesterId) {
    MovieList list = findOwned(listId, requesterId);
    if (list.getType() != ListType.CUSTOM) {
      throw new IllegalArgumentException("Default lists cannot be deleted");
    }
    listRepository.delete(list);
  }

  public MovieListDetailDto addMovie(Long listId, Long movieId, Long requesterId) {
    MovieList list = findOwned(listId, requesterId);
    Movie movie =
        movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
    if (list.containsMovie(movieId)) {
      throw DuplicateListException.movie(listId, movieId);
    }
    list.addMovie(movie);
    listRepository.flush(); // forca a constraint unique aqui (corrida entre dois pedidos)
    return toDetail(list);
  }

  public void removeMovie(Long listId, Long movieId, Long requesterId) {
    MovieList list = findOwned(listId, requesterId);
    if (!list.removeMovie(movieId)) {
      throw new ListItemNotFoundException(listId, movieId);
    }
  }

  // ---------- helpers ----------

  private MovieList findOwned(Long listId, Long requesterId) {
    MovieList list =
        listRepository.findById(listId).orElseThrow(() -> new ListNotFoundException(listId));
    if (!list.isOwnedBy(requesterId)) {
      if (list.getVisibility() == ListVisibility.PRIVATE) {
        throw new ListNotFoundException(listId);
      }
      throw new ListAccessDeniedException(listId);
    }
    return list;
  }

  private MovieList findVisible(Long listId, Long requesterId) {
    MovieList list =
        listRepository.findById(listId).orElseThrow(() -> new ListNotFoundException(listId));
    if (list.getVisibility() == ListVisibility.PRIVATE && !list.isOwnedBy(requesterId)) {
      throw new ListNotFoundException(listId);
    }
    return list;
  }

  private void ensureNameAvailable(Long ownerId, String name) {
    if (listRepository.existsByOwnerIdAndName(ownerId, name.trim())) {
      throw DuplicateListException.name(ownerId, name.trim());
    }
  }

  private void ensureDefaultLists(Long ownerId) {
    if (!listRepository.existsByOwnerIdAndType(ownerId, ListType.WATCHLIST)) {
      listRepository.save(
          new MovieList(
              ownerId,
              "Watchlist",
              "Movies I want to watch",
              ListType.WATCHLIST,
              ListVisibility.PRIVATE));
    }
    if (!listRepository.existsByOwnerIdAndType(ownerId, ListType.FAVORITES)) {
      listRepository.save(
          new MovieList(
              ownerId,
              "Favorites",
              "My favorite movies",
              ListType.FAVORITES,
              ListVisibility.PUBLIC));
    }
  }

  private MovieListSummaryDto toSummary(MovieList l) {
    return new MovieListSummaryDto(
        l.getId(),
        l.getOwnerId(),
        l.getName(),
        l.getDescription(),
        l.getType(),
        l.getVisibility(),
        l.getCreatedAt(),
        l.getItems().size());
  }

  private MovieListDetailDto toDetail(MovieList l) {
    List<ListItemResponseDto> items =
        l.getItems().stream()
            .map(
                i ->
                    new ListItemResponseDto(
                        i.getPosition(), i.getAddedAt(), MovieResponseDto.from(i.getMovie())))
            .toList();
    return new MovieListDetailDto(
        l.getId(),
        l.getOwnerId(),
        l.getName(),
        l.getDescription(),
        l.getType(),
        l.getVisibility(),
        l.getCreatedAt(),
        items);
  }
}
