package psia.movieRecHub.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import psia.movieRecHub.repository.ProfileRepository;

@Component
@ConditionalOnProperty(name = "profile.username-backfill.enabled",
    havingValue = "true", matchIfMissing = true)
public class ProfileUsernameBackfillWorker {

  private static final Logger log = LoggerFactory.getLogger(ProfileUsernameBackfillWorker.class);
  private final ProfileRepository profiles;
  private final ProfileUsernameClient users;
  private final int batchSize;
  private long lastId;

  public ProfileUsernameBackfillWorker(ProfileRepository profiles, ProfileUsernameClient users,
      @Value("${profile.username-backfill.batch-size}") int batchSize) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("Username backfill batch size must be positive");
    }
    this.profiles = profiles;
    this.users = users;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${profile.username-backfill.poll-delay}",
      initialDelayString = "${profile.username-backfill.poll-delay}")
  public void fillMissingUsernames() {
    List<Long> ids = profiles.findMissingUsernamesAfter(lastId, PageRequest.of(0, batchSize));
    if (ids.isEmpty() && lastId != 0) {
      lastId = 0;
      ids = profiles.findMissingUsernamesAfter(lastId, PageRequest.of(0, batchSize));
    }
    for (long userId : ids) {
      lastId = userId;
      try {
        profiles.updateMissingUsername(userId, users.usernameFor(userId));
      } catch (RestClientException exception) {
        log.warn("Username backfill pending for user {}", userId);
      }
    }
  }
}
