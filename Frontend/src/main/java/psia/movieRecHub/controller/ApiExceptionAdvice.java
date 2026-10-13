package psia.movieRecHub.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import psia.movieRecHub.client.ApiException;

/**
 * Safety net for errors a controller did not handle itself: an expired login goes back to the login
 * page, anything else shows its message on the movies page.
 */
@ControllerAdvice
public class ApiExceptionAdvice {

  @ExceptionHandler(ApiException.class)
  public String handle(ApiException exception, RedirectAttributes ra) {
    if (exception.isUnauthorized()) {
      ra.addFlashAttribute("error", "Please sign in to continue.");
      return "redirect:/login";
    }
    ra.addFlashAttribute("error", exception.getMessage());
    return "redirect:/movies";
  }
}
