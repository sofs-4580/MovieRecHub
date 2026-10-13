# Frontend

The **web application** of Movie Rec Hub and the only part meant to be opened in a browser: a Spring Boot + Thymeleaf app that renders every page and talks to the three backend services over HTTP. It has no database and no business logic of its own.

Part of the [Movie Rec Hub](../README.md) project.

```
browser ──► frontend :8080 ──► user-service     login, register, logout, password, session check
                           ├─► profile-service  browse / view / edit profiles
                           └─► movie-service    movies, reviews, lists
```

---

## Quick facts

| | |
|---|---|
| Container name | `frontend` |
| Port | `8080` → http://localhost:8080 |
| Database | none |
| Health check | `GET /actuator/health` |
| Depends on | `user-service`, `profile-service`, `movie-service` (all must be healthy) |
| Tech stack | Java 17 · Spring Boot 3.5 · Spring Web · Thymeleaf · Actuator |

---

## Pages

| Page | URL | Needs login |
|---|---|---|
| Movies (filter by genre), movie detail | `/movies`, `/movies/{id}` | no (reviews and "add to list" do) |
| Lists | `/lists`, `/lists/{id}` | own lists yes; public lists readable |
| Sign in / create account | `/login`, `/register` | – |
| People, public profile | `/profiles`, `/profiles/{id}` | yes |
| My profile | `/profile` | yes |
| Account, change password | `/account`, `/account/password` | yes |

`/` redirects to `/movies`.

### Actions (POST)

| URL | What it does |
|---|---|
| `/login`, `/register`, `/logout` | Authentication |
| `/movies/{id}/review` | Write a review (1–5 stars and a comment) |
| `/lists`, `/lists/add`, `/lists/{id}/remove`, `/lists/{id}/delete` | Create a list, add or remove a movie, delete a list |
| `/profile` | Save your profile |
| `/account/password` | Change your password |

---

## How login works

* `AuthWebController` sends the credentials to **user-service**, which creates the login session and
  answers with a `JSESSIONID` cookie. The frontend hands exactly that cookie to the browser.
* On every request `CurrentUser` reads `JSESSIONID` and asks user-service who it belongs to
  (once per request). movie-service is then called with the resulting `X-User-Id`, profile-service
  with the same cookie.
* This app has an HTTP session of its own (flash messages after redirects). It is configured with
  a different cookie name, `FRONTEND_SESSION`, otherwise it would overwrite the login cookie.
  **Do not remove `server.servlet.session.cookie.name`.**

## How errors are shown

Calls to the backends go through `ServiceCaller`, which turns every failure into an `ApiException`
(`503 SERVICE_UNAVAILABLE` when a service cannot be reached, `502 BAD_RESPONSE` for unexpected answers,
or the `code`/`message` the service sent). Controllers show the message on the page; whatever they do
not handle is caught by `ApiExceptionAdvice`: an expired login goes back to `/login`, anything else is
shown on the movies page. Calls time out after 3 s to connect and 10 s to read.

---

## Configuration

| Variable | Default | |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `USER_SERVICE_URL` | `http://localhost:8081` | UserService |
| `PROFILE_SERVICE_URL` | `http://localhost:8082` | ProfileService |
| `MOVIE_SERVICE_URL` | `http://localhost:8083` | MovieService |
| `LOG_LEVEL` | `INFO` | Root log level |

Default ports across the project: frontend 8080, user-service 8081, profile-service 8082,
movie-service 8083, recommendation-service 8084. In Docker the three service URLs are set by
`docker-compose.yml` to the container names (`http://user-service:8081`, …).

---

## Running

**With the whole stack** (recommended):

```bash
docker compose up --build
```

then open http://localhost:8080. The frontend starts last, after the three backends are healthy.

**On your machine** (Java 17 + Maven), with the backends already running:

```bash
cd Frontend
mvn spring-boot:run
```

Health: http://localhost:8080/actuator/health

> **Note:** after changing a template or the CSS in Docker, rebuild just this service with
> `docker compose up -d --build frontend`.

---

## Database

None. All data lives in the backend services' databases; see the READMEs of
[UserService](../UserService/README.md#database), [ProfileService](../ProfileService/README.md#database)
and [MovieService](../MovieService/README.md#database) for how to connect to them with DBeaver.

---

## Source layout

`client/` one class per backend (`AccountClient`, `ProfileServiceClient`, `MovieServiceClient`,
`UserSessionClient`) plus `ServiceCaller`, which turns HTTP errors into `ApiException`.
`controller/` one controller per area. `templates/fragments/layout.html` holds the sidebar, the
alert box and the genre chips shared by all pages.

```plaintext
Frontend/
├── Dockerfile
├── pom.xml
└── src/main/
    ├── java/psia/movieRecHub/
    │   ├── client/              --> HTTP clients for the backends + ServiceCaller / ApiException
    │   ├── config/              --> CurrentUser (request-scoped login), RestTemplateConfig
    │   ├── controller/          --> Auth, Account, Profile, Movie, MovieList controllers + ApiExceptionAdvice
    │   └── dto/                 --> Objects used by the pages
    └── resources/
        ├── application.properties
        ├── static/css/app.css
        └── templates/           --> Thymeleaf pages + fragments/layout.html
```

---

## Notes and limitations

- The movie filter in the UI is by genre only; title/director/actor/year search exists in MovieService's REST API but has no page yet.
- Login sessions are held by UserService in memory, so restarting it signs everybody out.
- The frontend trusts UserService's answer about who is logged in and passes that id to MovieService as `X-User-Id`.
- There is no HTTPS; set `SESSION_COOKIE_SECURE=true` on UserService once the site is served over it.
- Recommendations (RecommendationService) are not part of the UI yet.
