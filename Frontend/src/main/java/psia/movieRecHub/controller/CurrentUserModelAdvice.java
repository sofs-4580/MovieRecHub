package psia.movieRecHub.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import psia.movieRecHub.config.CurrentUser;

@ControllerAdvice
public class CurrentUserModelAdvice {

  private final CurrentUser currentUser;
  private final String accountFrontendUrl;

  public CurrentUserModelAdvice(
      CurrentUser currentUser, @Value("${account.frontend.url}") String accountFrontendUrl) {
    this.currentUser = currentUser;
    this.accountFrontendUrl = accountFrontendUrl;
  }

  @ModelAttribute
  public void addCurrentUser(Model model) {
    model.addAttribute("userId", currentUser.getId());
    model.addAttribute("userServiceUnavailable", currentUser.isUserServiceUnavailable());
    model.addAttribute("accountLoginUrl", accountFrontendUrl + "/#login");
  }
}
