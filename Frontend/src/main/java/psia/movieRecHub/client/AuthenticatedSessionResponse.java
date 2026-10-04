package psia.movieRecHub.client;

/** Answer of UserService GET /internal/auth/session. username may be null on older UserServices. */
public record AuthenticatedSessionResponse(long userId, String username) {}
