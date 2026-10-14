package psia.movieRecHub.engine;

import java.util.Set;

// o q sabemos acerca do gosto do user, usado pra gerar prompt pro llm
public record UserTaste(String tasteText, Set<Long> knownMovieIds) {

  public boolean isEmpty() {
    return tasteText.isBlank();
  }
}
