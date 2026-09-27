package psia.movieRecHub.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import psia.movieRecHub.dto.ChangePasswordRequest;
import psia.movieRecHub.service.AccountService;
import psia.movieRecHub.service.SessionService;

@RestController
public class AccountController {

  private final AccountService accounts;
  private final SessionService sessions;

  public AccountController(AccountService accounts, SessionService sessions) {
    this.accounts = accounts;
    this.sessions = sessions;
  }

  @PatchMapping(value = "/api/users/me/password", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> changePassword(
      HttpServletRequest httpRequest, @Valid @RequestBody ChangePasswordRequest request) {
    accounts.changePassword(sessions.requireUserId(httpRequest), request);
    return ResponseEntity.noContent().build();
  }
}
