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

## API (draft)
```
POST   /api/topics
GET    /api/topics
DELETE /api/topics/{id}

POST   /api/topic-queries           # create source-specific query under a topic
GET    /api/topic-queries?topicId=…
PATCH  /api/topic-queries/{id}      # enable/disable/update raw query/metadata
DELETE /api/topic-queries/{id}

GET    /api/series?topicId=…&window=1h|3h|24h&rollup=all|bySource
GET    /api/rankings?window=1h|3h|24h
GET    /api/health
GET    /api/metrics   (secured)
```

*Series response (rollup=all)*
```json
{
  "topicId": "t_42",
  "rollup": "all",
  "bucketGranularity": "minute",
  "buckets": [
    {"startUtc": "2025-11-04T12:00:00Z", "endUtc": "2025-11-04T12:01:00Z", "count": 45}
  ],
  "quality": {"partialBuckets": ["2025-11-04T12:59:00Z"]}
}
```
*Series response (rollup=bySource)*
```
{
  "topicId": "t_42",
  "rollup": "bySource",
  "series": [
    {
      "topicQueryId": "q_wiki",
      "source": "wiki-pageviews",
      "bucketGranularity": "hour",
      "buckets": [ { "startUtc": "...", "endUtc": "...", "count": 18 } ]
    },
    {
      "topicQueryId": "q_news",
      "source": "news-coverage",
      "bucketGranularity": "hour",
      "buckets": [ { "startUtc": "...", "endUtc": "...", "count": 27 } ]
    }
  ]
}
```

---

**Database (draft schema)**
```
topics(
  id                uuid primary key,
  display_name      text not null,
  created_at        timestamptz not null default now()
)

topic_queries(
  id                uuid primary key,
  topic_id          uuid not null references topics(id) on delete cascade,
  source            text not null,          -- e.g., "x-counts", "wiki-pageviews"
  raw_query         text not null,          -- exact string sent to the source
  query_metadata    jsonb not null default '{}'::jsonb,  -- versioned knobs
  active            boolean not null default true,
  created_at        timestamptz not null default now()
)
-- index for fetches within a time range per topic:
-- create index on topic_queries(topic_id, source);

counts(
  id                bigserial primary key,
  topic_query_id    uuid not null references topic_queries(id) on delete cascade,
  bucket_start_utc  timestamptz not null,
  bucket_end_utc    timestamptz not null,
  count             int not null check (count >= 0),
  quality           smallint not null default 0,  -- bit flags: partial, delayed, backfill
  unique(topic_query_id, bucket_start_utc, bucket_end_utc)
)
-- create index on counts(topic_query_id, bucket_start_utc);

anomalies(
  id                bigserial primary key,
  topic_id          uuid not null references topics(id) on delete cascade,
  topic_query_id    uuid,                    -- optional, for per-source diagnostics
  window            text not null,           -- '1h' | '3h' | '24h'
  method            text not null,           -- 'zscore'
  bucket_start_utc  timestamptz not null,
  score             double precision not null,
  threshold         double precision not null,
  is_hype           boolean not null
)
```

---

**Local Development**

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

---

**Security & Privacy**
-	Only aggregated counts are stored; no PII, no raw posts/articles are persisted.
-	Secrets via environment variables; never commit keys.
-	All timestamps at rest are UTC; UI renders in the user’s local time.


