package psia.movieRecHub.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.dto.LoginRequest;
import psia.movieRecHub.dto.LoginResponse;
import psia.movieRecHub.dto.RegisterRequest;
import psia.movieRecHub.dto.RegisterResponse;
import psia.movieRecHub.model.User;
import psia.movieRecHub.repository.UserRepository;

@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService tokenService;

  public AuthService(
      UserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
  }

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    if (users.existsByUsername(request.username())) {
      throw new UsernameTakenException();
    }

    String hash = passwordEncoder.encode(request.password());
    try {
      User saved = users.saveAndFlush(new User(request.username(), hash));
      return new RegisterResponse(saved.getId(), saved.getUsername());
    } catch (DataIntegrityViolationException exception) {
      // The unique constraint closes the race between the existence check and the insert.
      throw new UsernameTakenException();
    }
  }
  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest request) {
    User user = users.findByUsername(request.username()).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    return tokenService.issueFor(user);
  }
}
