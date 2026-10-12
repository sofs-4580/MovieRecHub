package psia.movieRecHub.engine;

import java.util.Set;

public record MovieDoc(
    long id,
    String title,
    int year,
    Set<String> genres,
    String synopsis,
    double avgRating,
    String director,
    String actors) {}
