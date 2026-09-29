package psia.movieRecHub.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import psia.movieRecHub.dto.UsernameResponse;
import psia.movieRecHub.repository.UserRepository;

@RestController
public class InternalUsernameController {

  private final UserRepository users;

  public InternalUsernameController(UserRepository users) {
    this.users = users;
  }

  @GetMapping("/internal/users/{userId}/username")
  public UsernameResponse username(@PathVariable long userId) {
    if (userId <= 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return users.findById(userId)
        .map(user -> new UsernameResponse(user.getId(), user.getUsername()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
