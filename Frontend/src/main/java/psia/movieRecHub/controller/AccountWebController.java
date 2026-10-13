package psia.movieRecHub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import psia.movieRecHub.client.AccountClient;
import psia.movieRecHub.client.ApiException;
import psia.movieRecHub.config.CurrentUser;

@Controller
public class AccountWebController {

  private final AccountClient accounts;
  private final CurrentUser currentUser;

  public AccountWebController(AccountClient accounts, CurrentUser currentUser) {
    this.accounts = accounts;
    this.currentUser = currentUser;
  }

  @GetMapping("/account")
  public String account() {
    return currentUser.getId() == null ? "redirect:/login" : "account";
  }

  @GetMapping("/account/password")
  public String passwordPage() {
    return currentUser.getId() == null ? "redirect:/login" : "password";
  }

  @PostMapping("/account/password")
  public String changePassword(
      @RequestParam String currentPassword,
      @RequestParam String newPassword,
      @RequestParam String confirmPassword,
      Model model,
      RedirectAttributes ra) {
    if (currentUser.getId() == null) {
      return "redirect:/login";
    }
    String problem = null;
    if (newPassword.length() < 12 || newPassword.length() > 128) {
      problem = "The new password must have between 12 and 128 characters.";
    } else if (!newPassword.equals(confirmPassword)) {
      problem = "The new passwords do not match.";
    }
    if (problem == null) {
      // An expired session surfaces as ApiException(401) and is handled by ApiExceptionAdvice.
      try {
        accounts.changePassword(currentUser.getSessionId(), currentPassword, newPassword);
        ra.addFlashAttribute("message", "Password changed.");
        return "redirect:/account";
      } catch (ApiException ex) {
        if (ex.isUnauthorized()) {
          throw ex;
        }
        problem = ex.getMessage();
      }
    }
    model.addAttribute("error", problem);
    return "password";
  }
}
