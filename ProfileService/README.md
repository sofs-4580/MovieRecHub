# ProfileService

The **public profile service** of Movie Rec Hub. It stores what users want others to know about them – display name, bio, favourite genres and favourite decades – and lets logged-in users browse each other's profiles.

Part of the [Movie Rec Hub](../README.md) project.

---

## What it does

| Responsibility | Details |
|---|---|
| Own profile | A logged-in user reads and edits their own profile |
| Browse profiles | Paginated list of all profiles, with search by username |
| View a profile | Public view of one user's profile |
| Initial profile creation | Receives the "create profile" call from UserService right after registration (idempotent) |
| Username backfill | Fills in missing usernames from UserService in the background |

**Not its job:** passwords and logins (UserService), movies, reviews and lists (MovieService).

## Tech stack

Java 17 · Spring Boot 3.5 · Spring Web · Spring Data JPA · Flyway · PostgreSQL 16 · Actuator

---

## Quick facts

| | |
|---|---|
| Container name | `profile-service` |
| Port | `8082` |
| Database | `profiles-db` → `movie_rec_hub_profiles` (host port `5434`) |
| Health check | `GET /actuator/health` |
| Depends on | `profiles-db`, `user-service` |
| Calls | UserService (`/internal/auth/session`, `/internal/users/{id}/username`) |
| Called by | Frontend, UserService |

---

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8082` | HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5434/movie_rec_hub_profiles` | JDBC URL |
| `DB_USERNAME` | `profile_service` | Database user |
| `DB_PASSWORD` | _(empty)_ | Database password |
| `USER_SERVICE_URL` | `http://localhost:8081` | Where UserService lives |
| `USER_CONNECT_TIMEOUT` | `2s` | Connect timeout towards UserService |
| `USER_READ_TIMEOUT` | `3s` | Read timeout towards UserService |
| `PROFILE_USERNAME_BACKFILL_ENABLED` | `true` | Turns the username backfill worker on/off |
| `PROFILE_USERNAME_BACKFILL_POLL_DELAY` | `30000` | Backfill interval (ms) |
| `PROFILE_USERNAME_BACKFILL_BATCH_SIZE` | `20` | Profiles handled per round |

---

## Database

PostgreSQL 16, container `profiles-db`. The schema is created and versioned by **Flyway** (`ddl-auto=validate`).

### Connecting (DBeaver, psql, …)

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5434` (override with `PROFILE_DB_PORT`) |
| Database | `movie_rec_hub_profiles` |
| User | `profile_service` |
| Password | _(leave empty)_ |

1. Start the database: `docker compose up -d profiles-db`
2. In DBeaver: **Database → New Database Connection → PostgreSQL**, fill in the values above, **Test Connection**, **Finish**.
3. Open `Databases → movie_rec_hub_profiles → Schemas → public → Tables`.

Without a client:

```bash
docker compose exec profiles-db psql -U profile_service -d movie_rec_hub_profiles
```

> **Note:** dev databases use `trust` authentication, so no password is needed and they are only reachable from `127.0.0.1`.

### Tables

**`profiles`**

| Column | Type | Notes |
|---|---|---|
| `user_id` | `BIGINT` | Primary key. It is the **user's id in UserService** – there is no foreign key across databases |
| `username` | `VARCHAR(30)` | Unique, `[a-z0-9_]{3,30}`. `NULL` until delivered or backfilled |
| `display_name` | `VARCHAR(100)` | Defaults to empty |
| `bio` | `VARCHAR(2000)` | Defaults to empty |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | Maintained by the service |

**`profile_favorite_genres`** – `(user_id, genre)`, one row per genre (`ACTION`, `COMEDY`, `DRAMA`, `HORROR`, `ROMANCE`, `SCI_FI`, `THRILLER`, `ANIMATION`, `DOCUMENTARY`, `FANTASY`). Deleted with the profile.

**`profile_favorite_decades`** – `(user_id, decade)`. Decades are multiples of 10 between 1880 and 2090 (for example `1990`).

### Migrations

| Version | File | Adds |
|---|---|---|
| V1 | `V1__create_profiles.sql` | `profiles`, `profile_favorite_genres` |
| V2 | `V2__create_profile_favorite_decades.sql` | `profile_favorite_decades` |
| V3 | `V3__add_profile_username.sql` | `username` column and unique index |

Add a new migration as `V4__description.sql` in `src/main/resources/db/migration/`. Never edit one that was already applied.

### Handy queries

```sql
SELECT user_id, username, display_name, updated_at FROM profiles ORDER BY user_id;

SELECT p.username, string_agg(g.genre, ', ') AS genres
FROM profiles p LEFT JOIN profile_favorite_genres g USING (user_id)
GROUP BY p.username;

-- Profiles still waiting for their username
SELECT user_id FROM profiles WHERE username IS NULL;
```

---

## API

### Authentication

The public endpoints need the **`JSESSIONID` cookie** issued by UserService at login. ProfileService does not keep sessions: it forwards the cookie to UserService (`GET /internal/auth/session`) on every call to learn the user id.

### Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/profiles/me` | Session | Own profile |
| `PATCH` | `/api/profiles/me` | Session | Partial update of own profile |
| `GET` | `/api/profiles` | Session | Browse profiles (paginated, searchable) |
| `GET` | `/api/profiles/{userId}` | Session | Public view of one profile |
| `PUT` | `/internal/profiles/{userId}` | – (internal) | Create the initial profile (called by UserService) |

**Own profile** – `GET /api/profiles/me`

```json
{
  "userId": 1,
  "displayName": "Alice",
  "bio": "Sci-fi fan",
  "favoriteGenres": ["SCI_FI", "DRAMA"],
  "favoriteDecades": [1980, 2010],
  "createdAt": "2026-10-03T22:10:00Z",
  "updatedAt": "2026-10-03T22:15:00Z"
}
```

**Update** – `PATCH /api/profiles/me` – send only the fields you want to change:

| Field | Rules |
|---|---|
| `displayName` | Up to 100 characters. `null` clears it |
| `bio` | Up to 2000 characters. `null` clears it |
| `favoriteGenres` | 1–10 valid genres (the list cannot be empty) |
| `favoriteDecades` | Up to 22 values, multiples of 10 from 1880 to 2090. `null` or `[]` clears them |

An empty body, unknown fields or invalid values are rejected with `400 INVALID_PROFILE_UPDATE`.

**Browse** – `GET /api/profiles?username=ali&page=0&size=20`

| Parameter | Default | Rules |
|---|---|---|
| `username` | – | Optional, **contains** match, `[a-z0-9_]`, up to 30 chars (case-insensitive) |
| `page` | `0` | `>= 0` |
| `size` | `20` | `1` to `50` |

```json
{
  "items": [{ "userId": 1, "username": "alice_01", "displayName": "Alice" }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Profiles without a username yet are not listed.

**One profile** – `GET /api/profiles/{userId}` returns `userId, username, displayName, bio, favoriteGenres, favoriteDecades, updatedAt`.

**Internal create** – `PUT /internal/profiles/{userId}` with `{"username": "alice_01", "favoriteGenres": ["SCI_FI"]}`. It never overwrites an existing profile: `201` means a new profile was created, `204` means it already existed (the username is only filled in if it was missing).

### Error codes

Errors have the shape `{"code": "...", "message": "..."}`.

| HTTP | `code` | When |
|---|---|---|
| `401` | `UNAUTHORIZED` | Missing or invalid session cookie |
| `404` | `PROFILE_NOT_READY` | Own profile does not exist **yet** (right after registration) |
| `404` | `PROFILE_NOT_FOUND` | No public profile with that id |
| `400` | `INVALID_PROFILE_UPDATE` | Bad `PATCH` body |
| `400` | `INVALID_PROFILE_QUERY` | Bad `username`, `page` or `size` |
| `400` | `INVALID_INITIAL_PROFILE` / `INVALID_USER_ID` | Bad internal call |
| `503` | `AUTH_SERVICE_UNAVAILABLE` | UserService cannot be reached to verify the session |

### Try it with curl

```bash
# 1. register and log in on UserService (see UserService/README.md) and keep the cookie
curl -s -c cookies.txt -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice_01","password":"a-long-password-123"}'

# 2. use the same cookie here
curl -b cookies.txt http://localhost:8082/api/profiles/me

curl -b cookies.txt -X PATCH http://localhost:8082/api/profiles/me \
  -H 'Content-Type: application/json' \
  -d '{"displayName":"Alice","bio":"Sci-fi fan","favoriteDecades":[1980,2010]}'

curl -b cookies.txt 'http://localhost:8082/api/profiles?username=ali'
```

---

## How it works

### Profiles are created asynchronously

Registration happens in UserService. A moment later its worker calls `PUT /internal/profiles/{userId}`; until then `GET /api/profiles/me` answers `PROFILE_NOT_READY`. The call is **idempotent**: repeating it never resets a profile the user has already edited.

### Username backfill

Profiles created before usernames were delivered have `username = NULL`. A scheduled worker (`ProfileUsernameBackfillWorker`) walks through them in batches and asks UserService `GET /internal/users/{id}/username`. If UserService is down it simply tries again on the next round.

---

## Running

**With the whole stack** (recommended):

```bash
docker compose up --build profile-service
```

**On your machine** (Java 17 + Maven):

```bash
docker compose up -d profiles-db user-service
cd ProfileService
mvn spring-boot:run
```

Health: http://localhost:8082/actuator/health

> **Note:** ProfileService needs UserService running to verify logins. Without it, protected endpoints answer `503 AUTH_SERVICE_UNAVAILABLE`.

---

## Project structure

```plaintext
ProfileService/
├── Dockerfile
├── compose.yaml                 --> profiles-db only
├── pom.xml
└── src/main/
    ├── java/psia/movieRecHub/
    │   ├── controller/          --> Own/Public/Internal profile controllers + one error handler each
    │   ├── service/             --> Own/Public/Initial profile services, session client, backfill worker
    │   ├── repository/          --> ProfileRepository
    │   ├── model/               --> Profile entity, MovieGenre enum
    │   └── dto/                 --> Request/response records
    └── resources/
        ├── application.properties
        └── db/migration/        --> Flyway V1..V3
```

---

## Notes and limitations

- Every protected request costs one HTTP call to UserService (the session is not cached).
- `/internal/**` endpoints have no authentication; keep them inside the private network.
- Profiles cannot be deleted, and there is no avatar or other media.
