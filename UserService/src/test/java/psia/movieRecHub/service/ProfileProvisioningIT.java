package psia.movieRecHub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import psia.movieRecHub.dto.RegisterRequest;
import psia.movieRecHub.dto.RegisterResponse;
import psia.movieRecHub.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileProvisioningIT {

  @Autowired private AuthService auth;
  @Autowired private ProfileProvisioningService provisioning;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactions;
  @MockitoBean private ProfileClient client;

  @BeforeEach
  void isolateDueJobsWithinRolledBackTestTransaction() {
    jdbc.update("UPDATE pending_profiles SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1 day'");
  }

  @Test
  void registrationPersistsAccountAndPendingDeliveryTogether() {
    RegisterResponse account = register();
    assertThat(users.existsById(account.userId())).isTrue();
    assertThat(pending(account.userId())).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT favorite_genres::text FROM pending_profiles WHERE user_id = ?",
        String.class, account.userId())).contains("DRAMA", "SCI_FI");
    assertThat(jdbc.queryForObject(
        "SELECT attempts FROM pending_profiles WHERE user_id = ?", Long.class, account.userId()))
        .isZero();
  }

  @Test
  void failedDeliveryRemainsPendingAndIsNotRetriedBeforeDueTime() {
    RegisterResponse account = register();
    doThrow(new ResourceAccessException("unavailable")).when(client).createInitialProfile(anyLong(), org.mockito.ArgumentMatchers.anySet());

    assertThat(provisioning.processNext()).isTrue();
    assertThat(pending(account.userId())).isEqualTo(1);
    assertThat(users.existsById(account.userId())).isTrue();
    assertThat(jdbc.queryForObject(
        "SELECT attempts FROM pending_profiles WHERE user_id = ?", Long.class, account.userId()))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT last_error FROM pending_profiles WHERE user_id = ?", String.class, account.userId()))
        .isEqualTo("DELIVERY_FAILED");
    assertThat(provisioning.processNext()).isFalse();
    verify(client, times(1)).createInitialProfile(account.userId(), Set.of("DRAMA", "SCI_FI"));
  }

  @Test
  void retryAfterRecoveryRemovesPendingDelivery() {
    RegisterResponse account = register();
    doThrow(new ResourceAccessException("unavailable")).doNothing()
        .when(client).createInitialProfile(account.userId(), Set.of("DRAMA", "SCI_FI"));
    assertThat(provisioning.processNext()).isTrue();
    jdbc.update("UPDATE pending_profiles SET next_attempt_at = CURRENT_TIMESTAMP WHERE user_id = ?",
        account.userId());

    assertThat(provisioning.processNext()).isTrue();
    assertThat(pending(account.userId())).isZero();
    assertThat(provisioning.processNext()).isFalse();
    verify(client, times(2)).createInitialProfile(account.userId(), Set.of("DRAMA", "SCI_FI"));
  }

  @Test
  void rolledBackRegistrationLeavesNeitherAccountNorPendingDelivery() {
    String username = username();
    TransactionTemplate transaction = new TransactionTemplate(transactions);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    long[] userId = new long[1];
    assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
      userId[0] = auth.register(new RegisterRequest(username, "password-testing-123", Set.of("DRAMA", "SCI_FI"))).userId();
      throw new IllegalStateException("simulate failure before commit");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(users.findByUsername(username)).isEmpty();
    assertThat(pending(userId[0])).isZero();
  }

  private RegisterResponse register() {
    return auth.register(new RegisterRequest(username(), "password-testing-123", Set.of("DRAMA", "SCI_FI")));
  }

  private String username() {
    return "profile_" + UUID.randomUUID().toString().substring(0, 8);
  }

  private long pending(long userId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM pending_profiles WHERE user_id = ?", Long.class, userId);
  }
}
