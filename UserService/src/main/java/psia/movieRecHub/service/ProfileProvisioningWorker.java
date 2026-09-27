package psia.movieRecHub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "profile.provisioning.enabled", havingValue = "true", matchIfMissing = true)
public class ProfileProvisioningWorker {

  private final ProfileProvisioningService provisioning;
  private final int batchSize;

  public ProfileProvisioningWorker(
      ProfileProvisioningService provisioning,
      @Value("${profile.provisioning.batch-size}") int batchSize) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("Profile batch size must be positive");
    }
    this.provisioning = provisioning;
    this.batchSize = batchSize;
  }

  @Scheduled(
      fixedDelayString = "${profile.provisioning.poll-delay}",
      initialDelayString = "${profile.provisioning.poll-delay}")
  public void deliverPendingProfiles() {
    for (int i = 0; i < batchSize; i++) {
      if (!provisioning.processNext()) {
        break;
      }
    }
  }
}
