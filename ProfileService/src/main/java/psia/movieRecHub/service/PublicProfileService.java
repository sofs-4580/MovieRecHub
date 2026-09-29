package psia.movieRecHub.service;

import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.dto.PublicProfilePageResponse;
import psia.movieRecHub.dto.PublicProfileResponse;
import psia.movieRecHub.dto.PublicProfileSummaryResponse;
import psia.movieRecHub.model.Profile;
import psia.movieRecHub.repository.ProfileRepository;

@Service
public class PublicProfileService {

  private final ProfileRepository profiles;

  public PublicProfileService(ProfileRepository profiles) {
    this.profiles = profiles;
  }

  @Transactional(readOnly = true)
  public PublicProfilePageResponse browse(String username, int page, int size) {
    if (page < 0 || size < 1 || size > 50) {
      throw new InvalidPublicProfileQueryException();
    }
    String query = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    if (query != null && !query.isEmpty() && !query.matches("[a-z0-9_]{1,30}")) {
      throw new InvalidPublicProfileQueryException();
    }
    PageRequest paging = PageRequest.of(page, size, Sort.by("userId").ascending());
    Page<Profile> result = query == null || query.isEmpty()
        ? profiles.findByUsernameIsNotNull(paging)
        : profiles.searchUsername(query, paging);
    return new PublicProfilePageResponse(
        result.map(PublicProfileSummaryResponse::from).getContent(),
        page, size, result.getTotalElements(), result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public PublicProfileResponse get(long userId) {
    if (userId <= 0) {
      throw new InvalidPublicProfileQueryException();
    }
    Profile profile = profiles.findById(userId)
        .filter(found -> found.getUsername() != null)
        .orElseThrow(PublicProfileNotFoundException::new);
    return PublicProfileResponse.from(profile);
  }
}
