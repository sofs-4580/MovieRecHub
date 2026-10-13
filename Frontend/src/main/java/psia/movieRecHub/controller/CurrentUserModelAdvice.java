package psia.movieRecHub.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import psia.movieRecHub.config.CurrentUser;

/** Makes the logged-in user available to every template (sidebar, "log in to ..." hints). */
@ControllerAdvice
public class CurrentUserModelAdvice {

  private final CurrentUser currentUser;

  public CurrentUserModelAdvice(CurrentUser currentUser) {
    this.currentUser = currentUser;
  }

  @ModelAttribute
  public void addCurrentUser(Model model) {
    model.addAttribute("userId", currentUser.getId());
    model.addAttribute("currentUsername", currentUser.getUsername());
    model.addAttribute("userServiceUnavailable", currentUser.isUserServiceUnavailable());
  }
}
