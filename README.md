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

**Constraints for Semester 1**
- Only official/public APIs or free/open datasets.
- No personal data; only aggregate counts.
- Focus on a stable loop: ingestion → analysis → dashboard.

---

## Data Sources & Legality

HypeFlow uses a **Source Adapter** layer so each source is a plug-in:

- **X (Twitter) “post counts”** — official API when a key is available (BYOK).  
- **Mastodon / ActivityPub** — public stats/trending endpoints.  
- **Open news feeds / datasets** — e.g., RSS, public news APIs, open data dumps.

Each adapter documents: supported query syntax, granularity, time zone, rate limits, and terms of service. The system stores only **aggregated counts** returned by a source.

---

## Data Normalization

All sources are mapped to a common schema:

- **Topic** — as configured by the user (plus optional normalized form).  
- **Bucket** — `[bucket_start_utc, bucket_end_utc)` in **UTC**.  
- **Count** — integer count for that bucket.  
- **Source** — adapter identifier (e.g., `x-counts`, `mastodon`, `news`).  
- **Query metadata** — the raw query sent to the source (for reproducibility).

---

## Technology Choices (what & why)

- **Java + Spring Boot** — reliable REST, scheduling, tests, Micrometer/Actuator; team familiarity.  
- **PostgreSQL (+ TimescaleDB optional)** — solid SQL + efficient time-series operations; easy Docker setup; Flyway migrations.  
- **React + Chart.js** — lightweight UI stack for interactive charts and CSV export.  
- **Gradle + GitHub Actions + Docker Compose** — predictable builds, CI from day one, reproducible local environment.   
- **Source Adapters** — clean separation per provider; allows BYO API keys without changing core logic.

---

## API (draft)
```
POST   /api/topics
GET    /api/topics
DELETE /api/topics/{id}

GET    /api/series?topicId=…&window=1h|3h|24h
GET    /api/rankings?window=1h|3h|24h
GET    /api/health
GET    /api/metrics   (secured)
```

**Series response (example)**
```json
{
  "topicId": "t_42",
  "granularity": "minute",
  "buckets": [
    {"startUtc": "2025-11-04T12:00:00Z", "endUtc": "2025-11-04T12:01:00Z", "count": 18},
    {"startUtc": "2025-11-04T12:01:00Z", "endUtc": "2025-11-04T12:02:00Z", "count": 27}
  ]
}
```

⸻

Database (draft schema)
```
topics(
  id                uuid primary key,
  display_name      text not null,
  raw_query         text not null,   -- what adapter receives
  source            text not null,   -- e.g., "x-counts", "mastodon", "news"
  granularity_hint  text,            -- minute/hour/day (preferred)
  created_at        timestamptz not null default now()
)

counts(
  id                bigserial primary key,
  topic_id          uuid not null references topics(id) on delete cascade,
  bucket_start_utc  timestamptz not null,
  bucket_end_utc    timestamptz not null,
  count             int not null,
  source            text not null,
  unique(topic_id, bucket_start_utc, bucket_end_utc)  -- idempotency
)

anomalies(
  id                bigserial primary key,
  topic_id          uuid not null references topics(id) on delete cascade,
  bucket_start_utc  timestamptz not null,
  method            text not null,   -- "zscore"
  score             double precision not null,
  threshold         double precision not null,
  is_hype           boolean not null
)
```

⸻

Local Development

# one-time
```sh
cp infra/example.env infra/.env
```

# start db
```sh
docker compose -f infra/docker-compose.yml up -d
```

# backend
```sh
./gradlew :api:bootRun       # API (exposes /actuator/health)
./gradlew :collector:run     # Scheduler
./gradlew :analyzer:run      # Analyzer
```

# frontend
```sh
cd web
npm i
npm run dev
```

Environment variables (sample):
```
DB_URL=jdbc:postgresql://localhost:5432/hypeflow
DB_USER=hypeflow
DB_PASS=hypeflow
ADAPTER_A_KEY=...
ADAPTER_B_KEY=...
```

⸻

Security & Privacy
-	Only aggregated counts are stored; no PII, no raw posts/articles are persisted.
-	Secrets via environment variables; never commit keys.
-	All timestamps at rest are UTC; UI renders in the user’s local time.


