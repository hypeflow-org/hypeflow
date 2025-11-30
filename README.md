# HypeFlow

**Status:** Semester 1 (MVP)  
**Goal:** Track how often topics are mentioned over time across selected, official data sources. Show time series, detect spikes, and let users subscribe to topics they care about.

---

## Repository

`https://github.com/hypeflow-org/hypeflow` 

---

## What HypeFlow Is

HypeFlow collects **aggregated mention counts** for user-defined topics (keywords/hashtags/queries).
It stores time-bucketed counts, computes a baseline, and flags **spikes** (“hype events”). A small web app lets users add topics, view charts for 1h / 3h / 24h windows, and export data.

For Semester 1 we only ingest sources that return time-bucketed counts out of the box (no local parsing/PII). If a source exposes only raw items, it’s out of scope for S1.

---

## Data Sources & Legality

HypeFlow uses a Source Adapter layer. Each adapter must return pre-aggregated, time-bucketed counts (minute/hour/day) for a given query or entity. No scraping. No PII.
	-	Examples suitable for S1 (non-exclusive):
	-	social/activity platforms that expose counts endpoints for queries/hashtags;
	-	open media datasets with bucketed coverage counts per query/entity;
	-	knowledge platforms with page-view or mention counters.
	-	BYO-key (bring-your-own API key) is supported per user and per source.

Each adapter doc includes: query syntax, bucket granularity & timezone, rate limits, auth flow, ToS notes, typical latency, deprecation risks.

---

## Data Normalization

Data Normalization

We normalize heterogeneous outputs into a canonical schema:
	-	Topic — user-defined concept (display name).
	-	TopicQuery — source-specific query for a Topic (stores raw query + metadata).
	-	Bucket — `[start_utc, end_utc)` in UTC.
	-	Count — integer for that bucket.
	-	Source — adapter ID (e.g., x-counts, news-coverage, wiki-pageviews).
	-	QueryMetadata — versioned metadata making runs reproducible.

Idempotency: uniqueness by `(topic_query_id, start_utc, end_utc)`.
Roll-up: series can be returned per source or sum across sources.

---

## Technology Choices (what & why)

- **Java + Spring Boot** — reliable REST, scheduling, tests, Micrometer/Actuator; team familiarity.  
- **PostgreSQL** — solid SQL + efficient time-series operations; easy Docker setup; Flyway migrations.  
- **React + Chart.js** — lightweight UI stack for interactive charts and CSV export.  
- **Gradle + GitHub Actions + Docker Compose** — predictable builds, CI from day one, reproducible local environment.   
- **Source Adapters** — clean separation per provider; allows BYO API keys without changing core logic.
- **Per-key rate limiting** — quotas are enforced per `(user_id, source_key)` to isolate users’ BYO keys and avoid noisy-neighbor effects.


---


## What it does right now
- Single backend in `backend/` (Java + Spring Boot).
- `/api/timeseries` endpoint: takes a word and date range, queries enabled sources, aggregates counts per UTC day, sums them, and returns JSON.
- No UI bundled yet; you can hit the API directly or wire your own chart.


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

Prereqs: JDK 17+, network access to external APIs; no database needed for this MVP.
---
## Notes on limits and legality
- NewsAPI: bounded pages; results are approximate for wide ranges. Respect their Terms and provide your own key.
- Reddit: presence of code != permission. Use only with explicit, compliant access per Reddit policies; otherwise leave it off.
- Wikipedia: public stats API; still be nice to their rate limits.