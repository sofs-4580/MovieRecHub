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
import psia.movieRecHub.repository.PendingProfileRepository;

@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final PendingProfileRepository pendingProfiles;

  public AuthService(
      UserRepository users, PasswordEncoder passwordEncoder, PendingProfileRepository pendingProfiles) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.pendingProfiles = pendingProfiles;
  }

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    if (users.existsByUsername(request.username())) {
      throw new UsernameTakenException();
    }

    String hash = passwordEncoder.encode(request.password());
    User saved;
    try {
      saved = users.saveAndFlush(new User(request.username(), hash));
    } catch (DataIntegrityViolationException exception) {
      // The unique constraint closes the race between the existence check and the insert.
      throw new UsernameTakenException();
    }
    pendingProfiles.enqueue(saved.getId());
    return new RegisterResponse(saved.getId(), saved.getUsername());
  }
  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest request) {
    User user = users.findByUsername(request.username()).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    return new LoginResponse(user.getId(), user.getUsername());
  }
}
