# HypeFlow

Student project (Semester 1, MVP). Goal: see how often a topic is mentioned over time using a few official data sources, return a simple daily time series, and experiment with spike detection. Non‑commercial, work in progress.

Repository: `https://github.com/hypeflow-org/hypeflow`

---

## What it does right now
- Single backend in `backend/` (Java + Spring Boot).
- `/api/timeseries` endpoint: takes a word and date range, queries enabled sources, aggregates counts per UTC day, sums them, and returns JSON.
- No UI bundled yet; you can hit the API directly or wire your own chart.

---

## Sources (current)
- `wikipedia` — Wikimedia Pageviews API (per-article, daily).
- `newsapi` — NewsAPI `/v2/everything`, limited pages → approximate, skewed to recent articles.
- `reddit` — Code exists but **disabled by default**; requires official Reddit API access and your own credentials. Only aggregates public `/search?sort=new&type=link` results by `created_utc`, up to ~1000 newest posts per query.

We do not scrape. We only keep aggregated counts (date + integer), no raw content or PII.

---

## API: POST `/api/timeseries`
Request:
```json
{
  "word": "bitcoin",
  "startDate": "2025-11-01",
  "endDate": "2025-11-07",
  "sources": ["wikipedia", "newsapi"]
}
```
Response:
```json
{
  "word": "bitcoin",
  "startDate": "2025-11-01",
  "endDate": "2025-11-07",
  "totalMentions": 42,
  "dailyStatistics": [
    {"date": "2025-11-01", "mentions": 3},
    {"date": "2025-11-02", "mentions": 0}
  ],
  "sources": ["wikipedia", "newsapi"],
  "fromCache": false
}
```

---

## Run locally (backend)
From `backend/`:
```bash
./mvnw spring-boot:run
```

Environment variables (see `backend/.env.example`, you can copy to `backend/.env`):
```bash
# NewsAPI
HYPEFLOW_NEWSAPI_API_KEY=your_newsapi_key_here

# Reddit (only if you have approved access)
HYPEFLOW_REDDIT_CLIENT_ID=your_reddit_client_id_here
HYPEFLOW_REDDIT_CLIENT_SECRET=your_reddit_client_secret_here
HYPEFLOW_REDDIT_USERNAME=your_reddit_username
```

`application.yml` maps them under `hypeflow.newsapi` and `hypeflow.reddit`. If Reddit vars are missing or you don’t have approved access, keep that source disabled.

Prereqs: JDK 21+, network access to external APIs; no database needed for this MVP.

---

## Notes on limits and legality
- NewsAPI: bounded pages; results are approximate for wide ranges. Respect their Terms and provide your own key.
- Reddit: presence of code != permission. Use only with explicit, compliant access per Reddit policies; otherwise leave it off.
- Wikipedia: public stats API; still be nice to their rate limits.
