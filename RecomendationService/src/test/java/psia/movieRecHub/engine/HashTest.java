package psia.movieRecHub.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class HashTest {

  @Test
  void matchesKnownSha256Value() {
    assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hash.sha256("abc"));
  }

  @Test
  void sameTextGivesSameHash() {
    assertEquals(Hash.sha256("Favorites: Heat [CRIME]."), Hash.sha256("Favorites: Heat [CRIME]."));
  }

  @Test
  void differentTextGivesDifferentHash() {
    assertNotEquals(Hash.sha256("Favorites: Heat."), Hash.sha256("Favorites: Heat; Alien."));
  }
}
