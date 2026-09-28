package psia.movieRecHub.repository;

import java.util.Set;

/** A durable request to create an initial profile, including its initial preferences. */
public record PendingProfileCreation(long userId, Set<String> favoriteGenres) {}
