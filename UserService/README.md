# UserService

The **identity and account service** of Movie Rec Hub. It owns user accounts and login sessions: registration, login, password change, and the "who is this cookie?" check that every other service relies on.

Part of the [Movie Rec Hub](../README.md) project.

---

## What it does

| Responsibility | Details |
|---|---|
| Registration | Creates an account with a unique username and a hashed password |
| Login | Verifies credentials and starts a session (`JSESSIONID` cookie) |
| Password change | Authenticated users can change their password |
| Session verification | Other services ask it who a session cookie belongs to |
| Profile provisioning | After registration, makes sure ProfileService creates the user's initial profile (retried until it works) |

**Not its job:** profile data (ProfileService), movies and reviews (MovieService), rendering pages (Frontend).

## Tech stack

Java 17 · Spring Boot 3.5 · Spring Web · Spring Data JPA · Flyway · PostgreSQL 16 · `spring-security-crypto` (PBKDF2 password hashing) · Actuator

---

## Quick facts

| | |
|---|---|
| Container name | `user-service` |
| Port | `8081` |
| Database | `users-db` → `movie_rec_hub_users` (host port `5433`) |
| Health check | `GET /actuator/health` |
| Depends on | `users-db` |
| Calls | ProfileService (`PUT /internal/profiles/{userId}`) |
| Called by | Frontend, ProfileService |

---

## Configuration

Every setting can be overridden with an environment variable. The defaults work when running on your machine against the Docker database.

| Variable | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8081` | HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5433/movie_rec_hub_users` | JDBC URL |
| `DB_USERNAME` | `user_service` | Database user |
| `DB_PASSWORD` | _(empty)_ | Database password |
| `SESSION_TIMEOUT` | `30m` | Idle time before a session expires |
| `SESSION_COOKIE_SECURE` | `false` | Set to `true` behind HTTPS |
| `PROFILE_SERVICE_URL` | `http://localhost:8082` | Where ProfileService lives |
| `PROFILE_CONNECT_TIMEOUT` | `2s` | Connect timeout towards ProfileService |
| `PROFILE_READ_TIMEOUT` | `3s` | Read timeout towards ProfileService |
| `PROFILE_PROVISIONING_ENABLED` | `true` | Turns the profile-creation worker on/off |
| `PROFILE_POLL_DELAY` | `5000` | Worker polling interval (ms) |
| `PROFILE_RETRY_DELAY` | `30s` | Wait before retrying a failed profile creation |
| `PROFILE_BATCH_SIZE` | `10` | Jobs handled per polling round |

In `docker-compose.yml` the database settings and `PROFILE_SERVICE_URL` are already set for you.

---

## Database

PostgreSQL 16, container `users-db`. The schema is created and versioned by **Flyway** (`ddl-auto=validate`, so Hibernate never changes it).

### Connecting (DBeaver, psql, …)

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5433` (override with `USER_DB_PORT`) |
| Database | `movie_rec_hub_users` |
| User | `user_service` |
| Password | _(leave empty)_ |

1. Start the database: `docker compose up -d users-db`
2. In DBeaver: **Database → New Database Connection → PostgreSQL**, fill in the values above, **Test Connection**, **Finish**.
3. Open `Databases → movie_rec_hub_users → Schemas → public → Tables`.

Without a client:

```bash
docker compose exec users-db psql -U user_service -d movie_rec_hub_users
```

> **Note:** dev databases use `trust` authentication, so no password is needed and they are only reachable from `127.0.0.1`.

### Tables

**`users`**

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | Primary key, generated |
| `username` | `VARCHAR(30)` | Unique. Format `[a-z0-9_]{3,30}` (checked by a constraint) |
| `password_hash` | `VARCHAR(255)` | PBKDF2 hash. The plain password is never stored |
| `created_at` | `TIMESTAMPTZ` | Defaults to now |

**`pending_profiles`** – the outbox for profile creation. A row exists only until ProfileService has confirmed the profile.

| Column | Type | Notes |
|---|---|---|
| `user_id` | `BIGINT` | Primary key and FK → `users(id)` (cascade delete) |
| `favorite_genres` | `JSONB` | Genres chosen at registration (array, max 10) |
| `created_at` | `TIMESTAMPTZ` | When the job was queued |
| `next_attempt_at` | `TIMESTAMPTZ` | When the worker may try again |
| `attempts` | `BIGINT` | Number of failed attempts |
| `last_error` | `VARCHAR(80)` | e.g. `HTTP_503` or `DELIVERY_FAILED` |

### Migrations

| Version | File | Adds |
|---|---|---|
| V1 | `V1__create_users.sql` | `users` table |
| V2 | `V2__create_pending_profiles.sql` | `pending_profiles` table |
| V3 | `V3__add_initial_favorite_genres_to_pending_profiles.sql` | `favorite_genres` column |

Add a new migration as `V4__description.sql` in `src/main/resources/db/migration/`. Never edit a migration that was already applied.

### Handy queries

```sql
SELECT id, username, created_at FROM users ORDER BY id;

-- Profiles that ProfileService has not confirmed yet
SELECT user_id, attempts, last_error, next_attempt_at FROM pending_profiles;

-- Which migrations ran?
SELECT version, description, success FROM flyway_schema_history;
```

---

## API

Errors always have the shape `{"code": "...", "message": "..."}`.

### Public endpoints

| Method | Path | Auth | Description | Success |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | – | Create an account | `201` |
| `POST` | `/api/auth/login` | – | Log in, returns the session cookie | `200` |
| `PATCH` | `/api/users/me/password` | Session | Change own password | `204` |

**Register** – `POST /api/auth/register`

```json
{
  "username": "alice_01",
  "password": "a-long-password-123",
  "favoriteGenres": ["SCI_FI", "DRAMA"]
}
```

| Field | Rules |
|---|---|
| `username` | 3–30 characters, `[a-z0-9_]` only (trimmed and lower-cased automatically) |
| `password` | 12–128 characters |
| `favoriteGenres` | Required, 1–10 values from `ACTION, COMEDY, DRAMA, HORROR, ROMANCE, SCI_FI, THRILLER, ANIMATION, DOCUMENTARY, FANTASY` |

Response: `{"userId": 1, "username": "alice_01"}`

**Login** – `POST /api/auth/login`

```json
{ "username": "alice_01", "password": "a-long-password-123" }
```

Response: `{"userId": 1, "username": "alice_01"}` plus a `Set-Cookie: JSESSIONID=...` header. Logging in always starts a **fresh** session.

**Change password** – `PATCH /api/users/me/password` (needs the `JSESSIONID` cookie)

```json
{ "currentPassword": "a-long-password-123", "newPassword": "another-long-password-456" }
```

### Internal endpoints

Used by other services. **Not authenticated** – only reachable inside the Docker network (or from localhost in dev).

| Method | Path | Used by | Description |
|---|---|---|---|
| `GET` | `/internal/auth/session` | Frontend, ProfileService | Returns `{"userId": N}` for the `JSESSIONID` cookie sent, or `401` |
| `GET` | `/internal/users/{userId}/username` | ProfileService | Returns `{"userId": N, "username": "..."}` or `404` |

### Error codes

| HTTP | `code` | When |
|---|---|---|
| `400` | `VALIDATION_ERROR` | A field breaks the rules above |
| `400` | `INVALID_REQUEST` | Malformed JSON |
| `401` | `INVALID_CREDENTIALS` | Wrong username or password (same answer for both on purpose) |
| `401` | `UNAUTHORIZED` | No valid session |
| `403` | `INVALID_CURRENT_PASSWORD` | `currentPassword` does not match |
| `409` | `USERNAME_TAKEN` | Username already exists |

### Try it with curl

```bash
# register
curl -i -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice_01","password":"a-long-password-123","favoriteGenres":["SCI_FI"]}'

# log in and keep the cookie in a file
curl -i -c cookies.txt -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice_01","password":"a-long-password-123"}'

# who am I? (internal endpoint)
curl -b cookies.txt http://localhost:8081/internal/auth/session
```

---

## How it works

### Sessions

The login session is a standard servlet session stored **in memory** in this service. The browser holds the `JSESSIONID` cookie (`HttpOnly`, `SameSite=Strict`, `Secure` only if enabled). The cookie carries no user data; the user id lives in the server-side session. Other services call `/internal/auth/session` to validate it.

### Profile provisioning (outbox pattern)

Registering writes the user **and** a `pending_profiles` row in one transaction, so a registration is never lost. A scheduled worker (`ProfileProvisioningWorker`) then:

1. locks the next due row (`FOR UPDATE SKIP LOCKED`, one job at a time),
2. calls `PUT /internal/profiles/{userId}` on ProfileService with the username and favourite genres,
3. deletes the row on `201`/`204`, or reschedules it (`PROFILE_RETRY_DELAY`) and records `last_error` on failure.

ProfileService treats the call as idempotent, so retries are safe.

---

## Running

**With the whole stack** (recommended):

```bash
docker compose up --build user-service
```

**On your machine** (Java 17 + Maven):

```bash
docker compose up -d users-db
cd UserService
mvn spring-boot:run
```

Check it is alive: http://localhost:8081/actuator/health → `{"status":"UP"}`

> **Note:** the first start takes up to a minute. The Docker healthcheck allows a 60 s start period.

---

## Project structure

```plaintext
UserService/
├── Dockerfile
├── compose.yaml                 --> users-db only
├── pom.xml
└── src/main/
    ├── java/psia/movieRecHub/
    │   ├── controller/          --> AuthController, AccountController, Internal* controllers, error handler
    │   ├── service/             --> AuthService, AccountService, SessionService, profile provisioning
    │   ├── repository/          --> UserRepository, PendingProfileRepository
    │   ├── model/               --> User entity
    │   ├── dto/                 --> Request/response records
    │   └── config/              --> PasswordConfig (PBKDF2), scheduling config
    └── resources/
        ├── application.properties
        └── db/migration/        --> Flyway V1..V3
```

---

## Notes and limitations

- Sessions live in memory: restarting the service logs everyone out, and it cannot run as several instances without a shared session store.
- `/internal/**` endpoints have no authentication; keep them inside the private network.
- Set `SESSION_COOKIE_SECURE=true` as soon as the site is served over HTTPS.
- Usernames cannot be changed.
