# MovieService

The **catalogue service** of Movie Rec Hub. It owns the movies, the reviews users write about them and the personal movie lists (Watchlist, Favorites and custom lists).

Part of the [Movie Rec Hub](../README.md) project.

---

## What it does

| Responsibility | Details |
|---|---|
| Catalogue | Lists movies and searches them by title, director, actor, genre or release year |
| Seeding | On first start, fills the empty catalogue with the top-rated movies from [TMDB](https://www.themoviedb.org/) |
| Reviews | Users review a movie with a 1–5 rating and a comment (one review per user per movie); the movie's average rating is recalculated automatically |
| Lists | Every user gets a private **Watchlist** and **Favorites** list, and can create custom public or private lists |

**Not its job:** accounts and logins (UserService), profiles (ProfileService), pages (Frontend).

## Tech stack

Java 17 · Spring Boot 3.5 · Spring Web · Spring Data JPA (Hibernate) · Bean Validation · PostgreSQL 16 · Actuator · H2 (tests only)

---

## Quick facts

| | |
|---|---|
| Container name | `movie-service` |
| Port | `8083` |
| Database | `movie-db` → `movie_rec_hub_movies` (host port `5435`) |
| Health check | `GET /actuator/health` |
| Depends on | `movie-db` |
| Calls | TMDB API (only to seed the catalogue) |
| Called by | Frontend, RecommendationService |

---

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8083` | HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5435/movie_rec_hub_movies` | JDBC URL |
| `DB_USERNAME` | `movie_service` | Database user |
| `DB_PASSWORD` | _(empty)_ | Database password |
| `TMDB_API_KEY` | _(empty)_ | TMDB key used to seed the catalogue |
| `JPA_DDL_AUTO` | `update` | Hibernate schema mode (`update`, `validate`, `none`, …) |
| `JPA_SHOW_SQL` | `false` | Print every SQL statement |
| `LOG_LEVEL` | `INFO` | Root log level |

In Docker the `TMDB_API_KEY` comes from your `.env` file at the project root.

---

## Database

PostgreSQL 16, container `movie-db`. Unlike User/Profile services there are **no Flyway migrations** here: Hibernate creates and updates the tables from the entities (`ddl-auto=update`).

### Connecting (DBeaver, psql, …)

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5435` (override with `MOVIE_DB_PORT`) |
| Database | `movie_rec_hub_movies` |
| User | `movie_service` |
| Password | _(leave empty)_ |

1. Start the database: `docker compose up -d movie-db`
2. In DBeaver: **Database → New Database Connection → PostgreSQL**, fill in the values above, **Test Connection**, **Finish**.
3. Open `Databases → movie_rec_hub_movies → Schemas → public → Tables`.

Without a client:

```bash
docker compose exec movie-db psql -U movie_service -d movie_rec_hub_movies
```

> **Note:** dev databases use `trust` authentication, so no password is needed and they are only reachable from `127.0.0.1`.

### Tables

| Table | Purpose | Key columns |
|---|---|---|
| `movie` | The catalogue | `id`, `title`, `release_year`, `synopsis`, `avg_rating`, `director`, `actors` |
| `movie_genres` | Genres of each movie | `movie_id`, `genres` (enum name, e.g. `SCI_FI`) |
| `review` | User reviews | `id`, `user_id`, `movie_id`, `rating`, `comment`, `created_at`. **Unique** `(user_id, movie_id)` |
| `movie_list` | Lists | `id`, `owner_id`, `name`, `description`, `type`, `visibility`, `created_at`. **Unique** `(owner_id, name)` |
| `movie_list_item` | Movies inside a list | `id`, `list_id`, `movie_id`, `position`, `added_at`. **Unique** `(list_id, movie_id)` |

> **Note:** `user_id` and `owner_id` are the user's id in UserService. There are no foreign keys to other databases, so MovieService never checks that the user exists.

Enums: `type` is `WATCHLIST`, `FAVORITES` or `CUSTOM`; `visibility` is `PUBLIC` or `PRIVATE`; genres are `ACTION, COMEDY, DRAMA, HORROR, ROMANCE, SCI_FI, THRILLER, ANIMATION, DOCUMENTARY, FANTASY`.

### Handy queries

```sql
SELECT id, title, release_year, avg_rating FROM movie ORDER BY avg_rating DESC LIMIT 10;

SELECT m.title, string_agg(g.genres, ', ') AS genres
FROM movie m JOIN movie_genres g ON g.movie_id = m.id
GROUP BY m.title;

SELECT l.name, l.type, l.visibility, count(i.id) AS movies
FROM movie_list l LEFT JOIN movie_list_item i ON i.list_id = l.id
GROUP BY l.id ORDER BY l.owner_id, l.name;
```

### Seeding the catalogue

When the service starts and the `movie` table is **empty**, `BdLoader` downloads the 2 first pages of TMDB's *top rated* movies (about 39) together with their director and main actors, and maps TMDB genres to the genres above.

- Needs a valid `TMDB_API_KEY`. Without it the logs show `[DataLoader] Falha ao popular a partir da TMDB` and the catalogue stays empty (the service itself still starts).
- If the table already has movies, the seed is skipped (`Base de dados ja populada`).
- To reseed: empty the table or recreate the volume (`docker compose down -v`).

---

## API

Errors follow [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) (`ProblemDetail`): `status`, `detail` and, when relevant, `message` and `errorCode`.

### Movies – `/api/movies`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/movies` | All movies. Optional `?genre=SCI_FI` |
| `GET` | `/api/movies/{id}` | One movie |
| `GET` | `/api/movies/search?query=nolan` | Free text over title, director and actors |
| `GET` | `/api/movies/search/title?title=...` | By title (contains, case-insensitive) |
| `GET` | `/api/movies/search/director?director=...` | By director |
| `GET` | `/api/movies/search/actor?actor=...` | By actor |
| `GET` | `/api/movies/year/{releaseYear}` | By release year |
| `PATCH` | `/api/movies/{id}/rating?newAvgRating=4.2` | Overwrite the average rating (internal use) |

Movie response:

```json
{
  "id": 1, "title": "Inception", "releaseYear": 2010,
  "genres": ["ACTION", "SCI_FI"], "synopsis": "...",
  "avgRating": 4.5, "director": "Christopher Nolan", "actors": "Leonardo DiCaprio, ..."
}
```

### Reviews – `/api/reviews`

| Method | Path | Description | Success |
|---|---|---|---|
| `POST` | `/api/reviews` | Create a review | `201` |
| `GET` | `/api/reviews/movie/{movieId}` | Reviews of a movie, most recent first | `200` |
| `GET` | `/api/reviews/user/{userId}` | Reviews written by a user | `200` |
| `DELETE` | `/api/reviews/{id}` | Delete a review (recalculates the average) | `204` |

```json
{ "userId": 1, "movieId": 3, "rating": 5, "comment": "Loved it" }
```

`rating` must be 1–5 and `comment` cannot be blank. A second review of the same movie by the same user is rejected with `409`.

### Lists – `/api/lists`

The acting user is identified by the **`X-User-Id` header**. It is required for anything that changes data; on `GET` it is optional (without it you only see public lists).

| Method | Path | Header | Description | Success |
|---|---|---|---|---|
| `POST` | `/api/lists` | required | Create a custom list | `201` |
| `GET` | `/api/lists/user/{userId}` | optional | Lists of a user (owner sees all, others only public). Default lists are created on first access | `200` |
| `GET` | `/api/lists/{id}` | optional | A list with its movies | `200` |
| `PATCH` | `/api/lists/{id}` | required | Change name, description or visibility (owner only, custom lists only) | `200` |
| `DELETE` | `/api/lists/{id}` | required | Delete a list (owner only, custom lists only) | `204` |
| `POST` | `/api/lists/{id}/movies/{movieId}` | required | Append a movie (owner only) | `201` |
| `DELETE` | `/api/lists/{id}/movies/{movieId}` | required | Remove a movie and renumber positions (owner only) | `204` |

Create a list:

```json
{ "name": "Rainy Sundays", "description": "Cosy picks", "visibility": "PUBLIC" }
```

- List names are unique per owner (`409` if repeated).
- The default **Watchlist** and **Favorites** lists cannot be renamed or deleted.
- A movie can appear only once in a list (`409`).

### Error codes

| HTTP | `errorCode` | When |
|---|---|---|
| `404` | `MOVIE_ERR_001` | Movie not found |
| `404` | `REVIEW_ERR_001` | Review not found |
| `404` | `LIST_ERR_001` | List, or movie in that list, not found. Also returned for someone else's **private** list, so its existence is not revealed |
| `409` | `REVIEW_ERR_002` | The user already reviewed this movie |
| `409` | `LIST_ERR_003` | Duplicate list name or movie already in the list |
| `409` | `DB_ERR_001` | Unique constraint hit by two concurrent requests |
| `403` | `LIST_ERR_002` | Trying to change a public list you do not own |
| `400` | – | Validation error, missing header or parameter, bad value |

### Try it with curl

```bash
curl 'http://localhost:8083/api/movies?genre=SCI_FI'
curl 'http://localhost:8083/api/movies/search?query=nolan'

curl -X POST http://localhost:8083/api/reviews \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"movieId":1,"rating":5,"comment":"Loved it"}'

curl -X POST http://localhost:8083/api/lists \
  -H 'X-User-Id: 1' -H 'Content-Type: application/json' \
  -d '{"name":"Rainy Sundays","description":"Cosy picks","visibility":"PUBLIC"}'

curl -X POST http://localhost:8083/api/lists/3/movies/1 -H 'X-User-Id: 1'
```

---

## How it works

### Who is the user?

MovieService has **no login of its own**. The Frontend checks the session with UserService and then calls MovieService with `X-User-Id`; reviews carry `userId` in the body. This means MovieService **trusts its callers**: anyone who can reach it directly can act as any user (there is a TODO in the code to replace this with a token or gateway). Keep it on the private network.

### Average rating

Creating or deleting a review recalculates `avg_rating` from all the movie's reviews. `PATCH /api/movies/{id}/rating` exists to set the value directly.

---

## Running

**With the whole stack** (recommended):

```bash
docker compose up --build movie-service
```

**On your machine** (Java 17 + Maven):

```bash
docker compose up -d movie-db
cd MovieService
export TMDB_API_KEY=your_key_here     # optional, only needed to seed
mvn spring-boot:run
```

Health: http://localhost:8083/actuator/health

## Tests

```bash
cd MovieService
mvn test
```

Covers entities, services (Mockito), repositories (H2 in memory) and controllers (MockMvc). No database or network is needed.

---

## Project structure

```plaintext
MovieService/
├── Dockerfile
├── compose.yaml                 --> movie-db only
├── pom.xml
└── src/
    ├── main/java/psia/movieRecHub/
    │   ├── controller/          --> Movie, Review, MovieList controllers + GlobalExceptionHandler
    │   ├── service/             --> MovieService, ReviewService, MovieListService
    │   ├── repositories/        --> Spring Data repositories
    │   ├── entities/            --> Movie, Review, MovieList, MovieListItem + enums
    │   ├── dto/                 --> request/, response/ and mdb/ (TMDB payloads)
    │   ├── exceptions/          --> Domain exceptions
    │   └── config/              --> BdLoader (TMDB seed), RestTemplate config
    ├── main/resources/application.properties
    └── test/                    --> Unit, repository and controller tests
```

---

## Notes and limitations

- Callers are trusted (`X-User-Id`, `userId` in the body); see [How it works](#how-it-works).
- The schema is managed by Hibernate, not by migrations. Switch to Flyway and `validate` before a real deployment.
- Search results are not paginated.
- The catalogue is limited to about 40 movies (2 TMDB pages) and is only seeded once.
