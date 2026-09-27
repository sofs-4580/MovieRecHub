package psia.movieRecHub.service;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import psia.movieRecHub.repository.PendingProfileRepository;

@Service
public class ProfileProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(ProfileProvisioningService.class);
  private final PendingProfileRepository pending;
  private final ProfileClient client;
  private final long retryDelayMillis;

  public ProfileProvisioningService(
      PendingProfileRepository pending,
      ProfileClient client,
      @Value("${profile.provisioning.retry-delay}") Duration retryDelay) {
    if (retryDelay.toMillis() <= 0) {
      throw new IllegalArgumentException("Profile retry delay must be positive");
    }
    this.pending = pending;
    this.client = client;
    this.retryDelayMillis = retryDelay.toMillis();
  }

  /** Processes at most one job, with a row lock held until the result is persisted. */
  @Transactional
  public boolean processNext() {
    Optional<Long> next = pending.lockNextDue();
    if (next.isEmpty()) {
      return false;
    }
    long userId = next.get();
    try {
      client.createInitialProfile(userId);
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
