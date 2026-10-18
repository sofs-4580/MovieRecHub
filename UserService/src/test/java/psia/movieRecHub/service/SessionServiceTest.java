package psia.movieRecHub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class SessionServiceTest {

  private final SessionService sessions = new SessionService();

  @Test
  void loginInvalidatesPreviousSessionAndStoresOnlyTheAccountId() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpSession oldSession = new MockHttpSession();
    oldSession.setAttribute("unrelated", "old-value");
    request.setSession(oldSession);
    sessions.start(request, 42L);
    assertThat(oldSession.isInvalid()).isTrue();
    assertThat(request.getSession(false).getId()).isNotEqualTo(oldSession.getId());
    assertThat(sessions.requireUserId(request)).isEqualTo(42L);
    assertThat(request.getSession(false).getAttribute("unrelated")).isNull();
  }

  @Test
  void unauthenticatedRequestDoesNotCreateASession() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    assertThatThrownBy(() -> sessions.requireUserId(request)).isInstanceOf(SessionRequiredException.class);
    assertThat(request.getSession(false)).isNull();
    request.getSession(true);
    assertThatThrownBy(() -> sessions.requireUserId(request)).isInstanceOf(SessionRequiredException.class);
  }

  @Test
  void invalidatedSessionNoLongerAuthenticatesTheAccount() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    sessions.start(request, 42L);
    request.getSession(false).invalidate();
    assertThatThrownBy(() -> sessions.requireUserId(request)).isInstanceOf(SessionRequiredException.class);
  }
}
