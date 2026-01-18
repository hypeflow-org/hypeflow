# HypeFlow

**Status:** Semester 1 (MVP)
**Goal:** Track how often topics are mentioned over time across selected, official data sources. Show time series, detect spikes, and let users subscribe to topics they care about.

---

## Quick Start (Docker)

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) (with Docker Compose)
- Git

### Quick Start

```bash
# Clone the repository
git clone https://github.com/hypeflow-org/hypeflow.git
cd hypeflow

# Copy environment template (optional - defaults work out of the box)
cp .env.example .env

# Start all services
docker compose up -d --build
```

### Access the Application

| Service | URL |
|---------|-----|
| Frontend (UI) | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Health Check | http://localhost:8080/api/health |

### Sample API Requests

**Health Check:**
```bash
curl http://localhost:8080/api/health
```

**Search for mentions of "bitcoin" (7 days):**
```bash
curl -X POST http://localhost:8080/api/timeseries \
  -H "Content-Type: application/json" \
  -d '{
    "word": "bitcoin",
    "startDate": "2025-01-10",
    "endDate": "2025-01-17",
    "sources": ["wikipedia", "hackernews", "arxiv"]
  }'
```

**Get search history:**
```bash
curl "http://localhost:8080/api/search/history/last?limit=10"
```

**Get popular searches:**
```bash
curl "http://localhost:8080/api/search/history/popular?limit=10"
```

### Running Without API Keys

HypeFlow works **out of the box** without any API keys! The following sources require no authentication:

| Source | Description | API Key Required |
|--------|-------------|------------------|
| Wikipedia | Page view statistics | No |
| HackerNews | Story mentions via Algolia | No |
| GDELT | Global news coverage | No |
| StackExchange | Question/answer mentions | No |
| ArXiv | Academic paper mentions | No |
| NewsAPI | News articles | **Yes** (optional) |
| Reddit | Subreddit mentions | **Yes** (optional) |

To enable optional sources, add your API keys to `.env`:
```bash
# NewsAPI - Get your key at: https://newsapi.org/register
HYPEFLOW_NEWSAPI_API_KEY=your_key_here

# Reddit - Create app at: https://www.reddit.com/prefs/apps
HYPEFLOW_REDDIT_CLIENT_ID=your_client_id
HYPEFLOW_REDDIT_CLIENT_SECRET=your_client_secret
```

### Stop Services

```bash
docker compose down
```

### View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f backend
```

---
### Clear cache
```bash
docker exec -it hypeflow-redis redis-cli --scan --pattern "ts:*" | xargs -r docker exec -i hypeflow-redis redis-cli DEL
docker exec -it hypeflow-redis redis-cli --scan --pattern "ts:err:*" | xargs -r docker exec -i hypeflow-redis redis-cli DEL
docker exec -it hypeflow-redis redis-cli --scan --pattern "ratelimit:*" | xargs -r docker exec -i hypeflow-redis redis-cli DEL
```
or use `FLUSHALL`. 
```bash
docker exec hypeflow-redis redis-cli FLUSHALL
```
Be aware: `By default, FLUSHALL will synchronously flush all the databases`

### Run only mysql & redis (to run backend locally)
```bash
docker compose up -d mysql redis
```

## Repository

`https://github.com/hypeflow-org/hypeflow`

---

## What HypeFlow Is

HypeFlow collects **aggregated mention counts** for user-defined topics (keywords/hashtags/queries).
HypeFlow aggregates daily mention counts for a keyword across multiple public sources (e.g., Wikipedia Pageviews, GDELT, HackerNews, arXiv). The backend exposes a simple API for timeseries queries and search history. Caching and rate limiting are enabled by default.

Spike detection and subscriptions are planned for later milestones.

For Semester 1 we only ingest sources that return time-bucketed counts out of the box (no local parsing/PII). If a source exposes only raw items, it's out of scope for S1.

---

## Data Sources & Legality

HypeFlow uses a Source Adapter layer. Each adapter currently supports pre-aggregated, time-bucketed counts for a given query or entity. No scraping. No PII.
	-	Examples suitable for S1 (non-exclusive):
	-	social/activity platforms that expose counts endpoints for queries/hashtags;
	-	open media datasets with bucketed coverage counts per query/entity;
	-	knowledge platforms with page-view or mention counters;
	-	BYO-key (bring-your-own API key) via .env (project-level) for now. Users and sources in the future.

Each adapter doc includes: query syntax, bucket granularity & timezone, rate limits, auth flow, ToS notes, typical latency, deprecation risks.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Compose                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌─────────┐    ┌─────────┐                               │
│   │  MySQL  │    │  Redis  │                               │
│   │  :3306  │    │  :6379  │                               │
│   └────┬────┘    └────┬────┘                               │
│        │              │                                     │
│        └──────┬───────┘                                     │
│               │                                             │
│        ┌──────┴──────┐                                      │
│        │   Backend   │ ← Spring Boot                        │
│        │    :8080    │                                      │
│        └──────┬──────┘                                      │
│               │                                             │
│        ┌──────┴──────┐                                      │
│        │  Frontend   │ ← Vue.js + Nginx                     │
│        │    :3000    │                                      │
│        └─────────────┘                                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Technology Choices (what & why)

- **Java 17 + Spring Boot** — REST API for timeseries + search history, scheduling, tests, Micrometer/Actuator;
- **MySQL** — solid SQL for search history persistence.
- **Redis** — caching for API responses and rate limiting.
- **Vue.js + Chart.js** — lightweight UI stack for interactive charts.
- **Docker Compose** — Quick Start, reproducible local environment.
- **Source Adapters** — clean separation per provider; allows BYO API keys without changing core logic.
- **Rate Limiting** — Redis-backed, 30 requests/minute per IP.

---

## API Endpoints

### Timeseries
```
POST   /api/timeseries              # Get mention counts for a word
```

### Search History
```
GET    /api/search/history/last     # Recent searches
GET    /api/search/history/popular  # Most searched words
```

### Health
```
GET    /api/health                  # Service health (MySQL + Redis)
```

### Response Examples

**Timeseries Response:**
```json
{
  "word": "bitcoin",
  "startDate": "2025-01-10",
  "endDate": "2025-01-17",
  "totalMentions": 12543,
  "dailyStatistics": [
    {"date": "2025-01-10", "mentions": 1823},
    {"date": "2025-01-11", "mentions": 1654}
  ],
  "sources": ["wikipedia", "hackernews"],
  "fromCache": false,
  "perSource": [
    {"source": "wikipedia", "totalMentions": 8234, "dailyStatistics": [...]},
    {"source": "hackernews", "totalMentions": 4309, "dailyStatistics": [...]}
  ],
  "errors": []
}
```

**Health Response:**
```json
{
  "status": "UP",
  "mysql": {"status": "UP", "database": "hypeflow"},
  "redis": {"status": "UP", "ping": "PONG"}
}
```

---

## Local Development (without Docker)

### Prerequisites
- Java 17+
- Maven
- Node.js 18+
- MySQL 8.0
- Redis

### Backend
```bash
cd backend
cp .env.example .env
mvn spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run serve
```

---

## Security & Privacy
-	Only aggregated counts are stored; no PII, no raw posts/articles are persisted.
-	Secrets via environment variables; never commit keys.
-	All timestamps at rest are UTC; UI renders in the user's local time.
-	Rate limiting: 30 requests/minute per IP address.
