package psia.movieRecHub.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.entities.KnownUser;
import psia.movieRecHub.repository.KnownUserRepository;

@Service
public class KnowUserService {

  private final KnownUserRepository repository;

  public KnowUserService(KnownUserRepository repository) {
    this.repository = repository;
  }

  // Garante que o utilizador existe, para o agente o conhecer mesmo sem perfil
  @Transactional
  public KnownUser register(Long userId) {
    return repository.findById(userId).orElseGet(() -> repository.save(new KnownUser(userId)));
  }

  // Guarda o perfil enviado pelo Frontend. Devolve true se algo mudou.
  @Transactional
  public boolean saveProfile(Long userId, Set<String> genres, Set<Integer> decades) {
    KnownUser user = register(userId);

    Set<String> cleanGenres = new HashSet<>();
    if (genres != null) {
      for (String g : genres) {
        if (g != null && !g.isBlank()) {
          cleanGenres.add(g.trim().toUpperCase());
        }
      }
    }

    Set<Integer> cleanDecades = new HashSet<>();
    if (decades != null) {
      for (Integer d : decades) {
        if (d != null) {
          cleanDecades.add(d);
        }
      }
    }

    boolean changed = user.updateProfile(cleanGenres, cleanDecades);
    repository.save(user);
    return changed;
  }

  @Transactional(readOnly = true)
  public Optional<KnownUser> find(Long userId) {
    return repository.findById(userId);
  }
}
