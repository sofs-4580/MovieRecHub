package psia.movieRecHub.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import psia.movieRecHub.client.AccountClient;
import psia.movieRecHub.client.AccountClient.LoginResult;
import psia.movieRecHub.client.ApiException;
import psia.movieRecHub.config.CurrentUser;
import psia.movieRecHub.dto.MovieGenre;

@Controller
public class AuthWebController {

  private static final String USERNAME_PATTERN = "[a-z0-9_]{3,30}";

  private final AccountClient accounts;
  private final CurrentUser currentUser;

  public AuthWebController(AccountClient accounts, CurrentUser currentUser) {
    this.accounts = accounts;
    this.currentUser = currentUser;
  }

  // ---------- login ----------

  @GetMapping("/login")
  public String loginPage() {
    return currentUser.getId() != null ? "redirect:/movies" : "login";
  }

  @PostMapping("/login")
  public String login(
      @RequestParam String username,
      @RequestParam String password,
      HttpServletResponse response,
      Model model,
      RedirectAttributes ra) {
    String name = username.trim().toLowerCase();
    try {
      LoginResult result = accounts.login(name, password);
      // The login session lives in UserService; hand its cookie to the browser as it is.
      for (String cookie : result.sessionCookies()) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie);
      }
      ra.addFlashAttribute("message", "Welcome back, " + result.username() + "!");
      return "redirect:/recommendations";
    } catch (ApiException ex) {
      model.addAttribute("error", ex.getMessage());
      model.addAttribute("formUsername", name);
      return "login";
    }
  }

  // ---------- register ----------

  @GetMapping("/register")
  public String registerPage(Model model) {
    if (currentUser.getId() != null) {
      return "redirect:/movies";
    }
    return registerForm(model, "", List.of());
  }

  @PostMapping("/register")
  public String register(
      @RequestParam String username,
      @RequestParam String password,
      @RequestParam String confirmPassword,
      @RequestParam(name = "favoriteGenres", required = false) List<MovieGenre> favoriteGenres,
      Model model,
      RedirectAttributes ra) {
    String name = username.trim().toLowerCase();
    List<MovieGenre> genres = favoriteGenres == null ? List.of() : favoriteGenres;

    String problem = null;
    if (!name.matches(USERNAME_PATTERN)) {
      problem = "Username must be 3 to 30 lowercase letters, digits or underscores.";
    } else if (password.length() < 12 || password.length() > 128) {
      problem = "Password must have between 12 and 128 characters.";
    } else if (!password.equals(confirmPassword)) {
      problem = "The passwords do not match.";
    } else if (genres.isEmpty()) {
      problem = "Pick at least one favourite genre.";
    }
    if (problem == null) {
      try {
        accounts.register(name, password, genres.stream().map(MovieGenre::name).toList());
        ra.addFlashAttribute("message", "Account created. You can sign in now.");
        return "redirect:/login";
      } catch (ApiException ex) {
        problem = ex.getMessage();
      }
    }
    model.addAttribute("error", problem);
    return registerForm(model, name, genres);
  }

  private String registerForm(Model model, String username, List<MovieGenre> selected) {
    model.addAttribute("genres", MovieGenre.values());
    model.addAttribute("formUsername", username);
    model.addAttribute("selectedGenres", selected);
    return "register";
  }

  // ---------- logout ----------

  @PostMapping("/logout")
  public String logout(HttpServletResponse response, RedirectAttributes ra) {
    String sessionId = currentUser.getSessionId();
    if (sessionId != null) {
      accounts.logout(sessionId);
    }
    ResponseCookie expired =
        ResponseCookie.from(CurrentUser.SESSION_COOKIE, "")
            .path("/")
            .maxAge(0)
            .httpOnly(true)
            .sameSite("Strict")
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
    ra.addFlashAttribute("message", "You have been signed out.");
    return "redirect:/login";
  }
}
