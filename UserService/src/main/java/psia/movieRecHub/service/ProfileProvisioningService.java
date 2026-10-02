package psia.movieRecHub.service;

import java.time.Duration;
import java.util.Optional;
import psia.movieRecHub.repository.PendingProfileCreation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import psia.movieRecHub.repository.PendingProfileRepository;
import psia.movieRecHub.repository.UserRepository;

@Service
public class ProfileProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(ProfileProvisioningService.class);
  private final PendingProfileRepository pending;
  private final UserRepository users;
  private final ProfileClient client;
  private final long retryDelayMillis;

  public ProfileProvisioningService(
      PendingProfileRepository pending, UserRepository users,
      ProfileClient client,
      @Value("${profile.provisioning.retry-delay}") Duration retryDelay) {
    if (retryDelay.toMillis() <= 0) {
      throw new IllegalArgumentException("Profile retry delay must be positive");
    }
    this.pending = pending;
    this.users = users;
    this.client = client;
    this.retryDelayMillis = retryDelay.toMillis();
  }

  /** Processes at most one job, with a row lock held until the result is persisted. */
  @Transactional
  public boolean processNext() {
    Optional<PendingProfileCreation> next = pending.lockNextDue();
    if (next.isEmpty()) {
      return false;
    }
    PendingProfileCreation creation = next.get();
    long userId = creation.userId();
    try {
      String username = users.findById(userId).orElseThrow().getUsername();
      client.createInitialProfile(userId, username, creation.favoriteGenres());
    } catch (RestClientException exception) {
      String error = exception instanceof RestClientResponseException response
          ? "HTTP_" + response.getStatusCode().value() : "DELIVERY_FAILED";
      pending.retryLater(userId, retryDelayMillis, error);
      log.warn("Profile creation pending for user {}: {}", userId, error);
      return true;
    }
    pending.complete(userId);
    return true;
  }
}
