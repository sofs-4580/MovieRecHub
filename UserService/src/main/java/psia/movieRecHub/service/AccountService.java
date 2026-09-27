package psia.movieRecHub.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.dto.ChangePasswordRequest;
import psia.movieRecHub.model.User;
import psia.movieRecHub.repository.UserRepository;

@Service
public class AccountService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  public AccountService(UserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public void changePassword(long userId, ChangePasswordRequest request) {
    User user = users.findByIdForPasswordChange(userId)
        .orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new CurrentPasswordMismatchException();
    }
    user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    // JPA persists the managed entity when the transaction commits; the row lock lasts until then.
  }
}
