# Frontend

The only web-facing service: a Spring Boot + Thymeleaf app that renders every page and talks to
the three backend services over HTTP.

```
browser ──► frontend :8080 ──► user-service     login, register, logout, password, session check
                           ├─► profile-service  browse / view / edit profiles
                           └─► movie-service    movies, reviews, lists
```

| Page | URL | Needs login |
|---|---|---|
| Movies, movie detail | `/movies`, `/movies/{id}` | no (reviews / "add to list" do) |
| Lists | `/lists`, `/lists/{id}` | own lists yes; public lists readable |
| Sign in / create account | `/login`, `/register` | – |
| People, public profile | `/profiles`, `/profiles/{id}` | yes |
| My profile | `/profile` | yes |
| Account, change password | `/account`, `/account/password` | yes |

## How login works

* `AuthWebController` sends the credentials to **user-service**, which creates the login session and
  answers with a `JSESSIONID` cookie. The frontend hands exactly that cookie to the browser.
* On every request `CurrentUser` reads `JSESSIONID` and asks user-service who it belongs to
  (once per request). movie-service is then called with the resulting `X-User-Id`, profile-service
  with the same cookie.
* This app has an HTTP session of its own (flash messages after redirects). It is configured with
  a different cookie name, `FRONTEND_SESSION`, otherwise it would overwrite the login cookie.
  **Do not remove `server.servlet.session.cookie.name`.**

## Configuration

| Variable | Default | |
|---|---|---|
| `SERVER_PORT` | `8080` | |
| `USER_SERVICE_URL` | `http://localhost:8081` | |
| `PROFILE_SERVICE_URL` | `http://localhost:8082` | |
| `MOVIE_SERVICE_URL` | `http://localhost:8083` | |

Default ports across the project: frontend 8080, user-service 8081, profile-service 8082,
movie-service 8083, recommendation-service 8084.

## Source layout

`client/` one class per backend (`AccountClient`, `ProfileServiceClient`, `MovieServiceClient`,
`UserSessionClient`) plus `ServiceCaller`, which turns HTTP errors into `ApiException`.
`controller/` one controller per area. `templates/fragments/layout.html` holds the sidebar, the
alert box and the genre chips shared by all pages.
