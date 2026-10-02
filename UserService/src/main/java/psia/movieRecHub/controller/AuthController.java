package psia.movieRecHub.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psia.movieRecHub.dto.LoginRequest;
import psia.movieRecHub.dto.LoginResponse;
import psia.movieRecHub.dto.RegisterRequest;
import psia.movieRecHub.dto.RegisterResponse;
import psia.movieRecHub.service.AuthService;
import psia.movieRecHub.service.SessionService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final SessionService sessions;

  public AuthController(AuthService authService, SessionService sessions) {
    this.authService = authService;
    this.sessions = sessions;
  }

  @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
  }
  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  public LoginResponse login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    LoginResponse account = authService.login(request);
    sessions.start(httpRequest, account.userId());
    return account;
  }
}
