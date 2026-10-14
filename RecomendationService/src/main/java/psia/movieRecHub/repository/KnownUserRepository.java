package psia.movieRecHub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import psia.movieRecHub.entities.KnownUser;

@Repository
public interface KnownUserRepository extends JpaRepository<KnownUser, Long> {}
