# Typed-request prompt tests

`prompt-tests.sh` sends 22 typed requests to `POST /api/recommendations` and prints, for each one,
the source (LLM or fallback), the movies returned (marking those the user already saw) and the latency.

```bash
./RecomendationService/scripts/prompt-tests.sh [userId] [seen ids, comma separated]
# defaults: user 5, seen = 7,8,9,10,12,13,14,30,32,38
```

Setup: `Qwen2.5-1.5B-Instruct Q4_K_M` on Ollama, `temperature 0` (deterministic, so each request
is run once), JSON-schema structured output, CPU only, catalogue of 39 TMDB top-rated movies in the prompt.

## Results (before -> after the prompt changes)

The changes were prompt-only: the "Already seen" id list, the decade in each catalogue line with
decade examples, and a rule for vague requests. The code only validates structure (ids exist, no
duplicates, count between 1 and 5).

| Metric | Before | After |
|---|---|---|
| Fallbacks (invalid LLM output) | 2/22 | 0/22 |
| Requests returning an already seen movie | 8/20 | 4/22 |
| Correct number of movies | 14/21 | 20/22 |
| Decade requests fully correct | 0/4 | 0/4 |
| Latency per typed request | 5-7 s | 5-8 s |

## What works

- **Count**: "two movies", "three comedies", "some movies", requests without a number (5). Only
  "a movie" fails (returns 5 instead of 1).
- **Combined requests**: "a comedy from the 90s", "a romantic drama", "two animated movies for kids".
- **Typos and other languages**: "recomend me a comedi", "quero um filme de comedia".
- **Prompt injection**: "Ignore all previous instructions..." is not obeyed.
- **Seen movies**: they only show up when the request itself pushes towards them
  (decade or "drama" requests).

## What does not work

- **Decades**: "the 90s", "the 70s", "before 1980" and "2020s" are not respected. When the model
  cannot resolve the request it tends to pick the first entries of the catalogue
  ("before 1980" and the injection test return the same 5 recent movies).
- **Single-genre requests**: "a romance movie", "an animation movie" and "a thriller" return
  movies of other genres.
- **"a movie"**: ignores the number 1.
- **Seen vs request conflict**: if the request only matches seen movies, the model prefers the
  request and returns a seen movie.

## Why we stop here

These failures are limits of a 1.5B quantized model running on CPU (weak at numeric filters such as
decades), not of the prompt or of the schema. Changing the prompt further gave no gain.
The catalogue itself is also limited: 39 mostly-drama movies, with no horror and almost no sci-fi,
and the schema forces ids, so the model cannot answer "none".

We kept the rule that the code does not filter or re-rank what the LLM chooses. The LLM proposes
and the code only validates structure.

Possible future work:
- a 3B model (about twice the latency);
- the LLM extracts filters (genre, decade, count) and the catalogue is pre-filtered before the prompt
  (this also removes the context-size limit on the catalogue);
- a larger catalogue with retrieval.
