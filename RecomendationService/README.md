# RecommendationService

The **AI recommendation service** of Movie Rec Hub. It suggests movies to a user in two ways: automatically, from what the user already likes (lists, reviews, favourite genres and decades), and on demand, from a free-text request such as *"two comedies from the 90s"*. A small local language model (Qwen2.5 1.5B, served by [Ollama](https://ollama.com/)) makes the choice.

Part of the [Movie Rec Hub](../README.md) project.

---

## What it does

| Responsibility | Details |
|---|---|
| Typed requests | `POST` a text and get 1 to 5 movies back (about 6 seconds on a CPU) |
| Recommendations "For you" | A background agent prepares 5 movies per user and stores them, so opening the page only reads a cache |
| Taste | Builds a short text from the user's lists, reviews and favourite genres/decades, and remembers which movies the user already knows |
| Safety net | If the model is down or answers nonsense, it returns the best-rated movies instead (`source: FALLBACK`) |

**Not its job:** the catalogue, reviews and lists (MovieService), profiles (ProfileService), accounts (UserService), pages (Frontend).

## Tech stack

Java 17 · Spring Boot 3.5 · Spring Web · Spring Data JPA (Hibernate) · PostgreSQL 16 · Actuator + Micrometer/Prometheus · Ollama (`/api/chat` with JSON-schema structured output) · JUnit 5 + Mockito

---

## Quick facts

| | |
|---|---|
| Container name | `recommendation-service` |
| Port | `8084` |
| Database | `reco-db` → `movie_rec_hub_recommendations` (host port `5436`) |
| Health check | `GET /actuator/health` |
| Metrics | `GET /actuator/prometheus` (HTTP latency histograms) |
| Depends on | `reco-db`, `movie-service`, `ollama` |
| Calls | MovieService (catalogue, lists, reviews), Ollama (the model) |
| Called by | Frontend |
| Model | `hf.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF:Q4_K_M` (downloaded by the `ollama-pull` service) |

---

## API

All endpoints are under `/api/recommendations`. The user comes in the `X-User-Id` header, which the Frontend fills after checking the login session.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/recommendations` | Typed request. Body `{"text": "a comedy from the 90s"}`. `X-User-Id` is optional (it only adds the user's taste) |
| `GET` | `/api/recommendations` | The prepared recommendations of the user (`X-User-Id` required) |
| `PUT` | `/api/recommendations/users/{userId}/profile` | Frontend pushes the user's favourite genres and decades. Body `{"favoriteGenres": ["DRAMA"], "favoriteDecades": [1990]}`. Answers `204` |

Answer of the first two:

```json
{
  "source": "LLM",
  "items": [
    { "movieId": 21, "title": "Dilwale Dulhania Le Jayenge", "year": 1995,
      "genres": ["COMEDY", "DRAMA", "ROMANCE"], "reason": "Genres: COMEDY, DRAMA, ROMANCE" }
  ]
}
```

`source` is `LLM` (chosen by the model), `FALLBACK` (best rated movies, the model was unavailable or gave no valid ids) or `PENDING` (`GET` only: nothing prepared yet, try again later).

```bash
curl -s -X POST localhost:8084/api/recommendations \
  -H 'Content-Type: application/json' -H 'X-User-Id: 5' \
  -d '{"text":"two animated movies"}'
```

---

## How it works

### Two paths, one rule: the login never waits for the model

- **"For you" (cache).** `GET` only reads the database, about 0.2 s. A background agent fills it. It skips users with no signals, users whose taste text did not change (same hash) and stops after 3 model calls per run. Only results chosen by the model are cached, so a fallback is retried on the next run.
- **Typed request.** `POST` asks the model right away (5 to 7 s). If the user is known, their taste and seen movies go in the prompt. If the taste cannot be loaded, the request still works without it.

### The taste of a user

A short text, built from three sources and sorted so that the same data always gives the same text (and the same hash):

- the profile: *"Favorite genres: DRAMA, SCI_FI. Favorite decades: 1970s, 1990s."*
- the lists: `Favorites`, `Watchlist`, `Other lists`, with the 8 most recent movies of each
- the reviews: `Rated highly` (4 or 5) and `Rated poorly` (1 or 2)

Separately, the service keeps the full set of movie ids the user has in lists or reviews. They go in the prompt as **"Already seen"**, so the model is told not to recommend them.

### The prompt and the model

The whole catalogue (about 40 movies, one line each: `id | title (year) | decade | genres`) goes at the start of the prompt, and the request at the end. That way Ollama reuses its prefix cache between requests. The model must answer with JSON that follows a schema (structured output), `temperature` is 0 and the context is 4096 tokens.

The model decides which movies to recommend. The code only checks structure: every id exists in the catalogue, there are no duplicates and the count is between 1 and 5. It never filters or re-ranks by content.

**Why a 1.5B model:** it runs on CPU only, with no GPU. The 0.5B model was too weak to follow the request, and bigger models are too slow for a typed request on the project's machines.

---

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8084` | HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5436/movie_rec_hub_recommendations` | JDBC URL |
| `DB_USERNAME` | `reco_service` | Database user |
| `DB_PASSWORD` | _(empty)_ | Database password |
| `DB_POOL_SIZE` | `8` | Connection pool size |
| `JPA_DDL_AUTO` | `update` | Hibernate schema mode |
| `MOVIE_SERVICE_URL` | `http://localhost:8083` | MovieService address |
| `MOVIE_CONNECT_TIMEOUT_MS` / `MOVIE_READ_TIMEOUT_MS` | `2000` / `5000` | MovieService timeouts |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama address |
| `LLM_MODEL` | `hf.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF:Q4_K_M` | Model name in Ollama |
| `LLM_READ_TIMEOUT_MS` | `180000` | The first call also loads the model, so it is long |
| `LLM_NUM_PREDICT` / `LLM_NUM_CTX` | `200` / `4096` | Max answer tokens / context size |
| `LLM_KEEP_ALIVE` | `30m` | How long Ollama keeps the model in memory |
| `CATALOGUE_REFRESH_INTERVAL_MS` | `60000` | How often the catalogue is reloaded from MovieService |
| `AGENT_INITIAL_DELAY_MS` / `AGENT_INTERVAL_MS` | `30000` / `300000` | When the background agent runs |
| `AGENT_MAX_PER_RUN` | `3` | Model calls per agent run |
| `LOG_LEVEL` | `INFO` | Root log level |
| `RECO_LOG_LEVEL` | `INFO` | Level of the `service` package. `DEBUG` logs the full prompt sent to the model |

---

## Database

PostgreSQL 16, container `reco-db`. Hibernate creates the tables from the entities (`ddl-auto=update`).

| Field | Value |
|---|---|
| Host / port | `localhost` / `5436` (override with `RECO_DB_PORT`) |
| Database / user | `movie_rec_hub_recommendations` / `reco_service` |
| Password | _(leave empty, dev databases use `trust`)_ |

```bash
docker compose exec reco-db psql -U reco_service -d movie_rec_hub_recommendations
```

| Table | Purpose |
|---|---|
| `recommendation_cache` | One row per user: `items` (jsonb), `source`, `model`, `input_hash` (SHA-256 of the taste text), `generated_at` |
| `known_user` | Users the service has seen, and when their profile last changed (collection tables hold their favourite genres and decades) |

---

## Running it

With the whole stack (recommended):

```bash
docker compose up --build        # ollama-pull downloads the model the first time (about 1 GB)
```

The first start of the stack is slow. Until the model is downloaded, typed requests answer with `FALLBACK`.

Only the service, from your IDE or Maven:

```bash
docker compose up -d reco-db ollama ollama-pull movie-service
cd RecomendationService && mvn spring-boot:run
```

To see exactly what the model receives, set `RECO_LOG_LEVEL=DEBUG` in `.env` and recreate the container:

```bash
docker compose up -d --force-recreate recommendation-service
docker compose logs recommendation-service | grep -A60 "Prompt sent"
```

---

## Tests

```bash
cd RecomendationService && mvn test
```

Unit tests (JUnit 5 + Mockito, no database and no Ollama) cover the prompt builder, the validation of the model's answer, the fallback, the background agent (hash check, per-run limit, a failing user does not stop the others), the taste text, the Ollama request and its errors, the catalogue refresh and the REST controller.

The quality of the answers is measured with a script against the real model: [`scripts/prompt-tests.sh`](scripts/prompt-tests.sh) sends 22 typed requests (quantity, genre, decade, combined, vague, typos, injection) and reports what comes back. Results and conclusions are in [`scripts/prompt-test.md`](scripts/prompt-test.md).

```bash
./RecomendationService/scripts/prompt-tests.sh [userId] [seen ids, comma separated]
```

---

## Known limitations

| Limitation | Detail |
|---|---|
| Small model | The 1.5B model follows quantity and combined requests but fails decades ("the 90s") and some single-genre requests. When lost it tends to answer with the first ids of the catalogue. It also follows instructions written inside the request (prompt injection), though the code only accepts catalogue ids, so the damage is limited to which catalogue movies are shown |
| Not fully repeatable | Even with `temperature` 0, the same request can differ between runs (Ollama cache state), and the quality depends on how much taste data the user has |
| Small catalogue | About 40 TMDB top-rated movies, mostly drama. The whole catalogue is inside the prompt, so it cannot grow much (4096 tokens). Retrieval or pre-filtering would be needed |
| Cannot say "none" | The answer schema forces ids, so a request nothing matches still returns movies |
| "Already seen" is a prompt rule | The model is asked to skip seen movies, but when the request conflicts with it the model prefers the request |
| Users without signals | The agent skips users with no lists, reviews or profile, so they get `PENDING` |
| Fallback is not cached | A failed model call is retried in the next agent run (at most 3 model calls per run) |
| Trust between services | `X-User-Id` is trusted as sent. Ports are bound to `127.0.0.1` and only the Frontend calls this service; there is no authentication between services |
| Latency | 5 to 7 s per typed request on a CPU laptop, about 14 s on the first call after a restart. It is slower on shared cloud CPUs |
