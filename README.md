# Smart Meeting Scheduler

A Java 21 and Spring Boot project for exploring calendar availability, time-zone-aware scheduling, meeting lifecycle APIs, and AI-assisted meeting requests. It is organized as a Maven multi-module project with five independently runnable services and a small shared library.

This is a learning and portfolio project; it is not presented as production-ready.

## Key features

- User profiles with IANA time zones and weekly working hours.
- Calendar events persisted as UTC instants and single-user free-time calculation.
- Multi-user slot suggestions with REQUIRED and OPTIONAL participant rules.
- Meeting request, update, suggestion, confirmation, retrieval, and cancellation APIs.
- Deterministic mock prompt parsing and an optional OpenAI-compatible structured-output provider.
- AI orchestration through backend APIs; the AI service has no database access and never confirms a meeting.
- Local process launch and Docker Compose support.

## Technology stack

| Area | Technologies |
|---|---|
| Language/runtime | Java 21 |
| Framework | Spring Boot 3.5.5, Spring Web |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL 16, Flyway |
| Validation/API docs | Jakarta Bean Validation, springdoc OpenAPI / Swagger UI |
| Tests | JUnit 5, Mockito, Spring Boot Test |
| Build/deployment | Maven reactor, Docker, Docker Compose |
| Time | java.time, Instant, ZoneId, IANA time-zone database |

## Architecture

Services communicate synchronously over REST. Each database-backed service owns its schema and migration. Scheduling and AI Meeting are stateless and database-free.

~~~mermaid
flowchart LR
  Client[Client] --> U[User Service :8081]
  Client --> C[Calendar Service :8082]
  Client --> S[Scheduling Service :8083]
  Client --> M[Meeting Service :8084]
  Client --> A[AI Meeting Service :8085]
  C -->|user profile and working hours| U
  S -->|availability windows| C
  M -->|user validation and organizer zone| U
  M -->|suggestions and confirmation recheck| S
  A -->|create meeting and request suggestions| M
  U -->|user_service schema| DB[(PostgreSQL)]
  C -->|calendar_service schema| DB
  M -->|meeting_service schema| DB
~~~

No service reads another service's database. The shared Maven module contains common technical/domain primitives, not service entities or repositories.

## Services and ports

| Service | Port | Responsibility | Data |
|---|---:|---|---|
| User Service | 8081 | Profiles, normalized email, IANA zone, weekly working hours | PostgreSQL schema user_service |
| Calendar Service | 8082 | Event lifecycle and single-user availability | PostgreSQL schema calendar_service |
| Scheduling Service | 8083 | Required interval intersection, candidate generation, optional attendance, deterministic ranking | Stateless |
| Meeting Service | 8084 | Meeting/participant persistence, lifecycle, scheduling delegation, confirmation revalidation | PostgreSQL schema meeting_service |
| AI Meeting Service | 8085 | Prompt parsing and meeting orchestration over Meeting Service APIs | Stateless |
| Shared | — | API error types, HTTP configuration, shared value objects | None |

User and Calendar contain repeatable development seeders. They do not overwrite existing user preferences or working hours and are disabled for test, prod, and production profiles.

## Main workflows

### User and working hours
Create a user with an email and IANA ZoneId such as Asia/Kolkata. Weekly intervals use weekday names and local HH:mm values. Multiple intervals per day are supported.

### Calendar availability
Calendar events use Instant and PostgreSQL TIMESTAMPTZ. Availability converts each user's local working hours through their ZoneId, clips intervals to the requested window, and subtracts overlapping BUSY events. Intervals are half-open [start, end). Event deletion is a soft cancellation; CANCELLED events do not block availability.

### Multi-user scheduling
Scheduling Service gets each user's free intervals through Calendar Service. It intersects sorted REQUIRED participant intervals using two pointers, generates starts at the configured granularity, and ranks results deterministically. The organizer is treated as REQUIRED. OPTIONAL participants never block a slot; available optional attendance improves rank. The preferred start time is a soft preference. Comparisons use UTC instants, while results include participant-local zoned times.

### Meeting lifecycle
Meeting Service creates REQUESTED meetings, manages participants, requests suggestions, confirms a selected slot, and cancels without deleting the record. Confirmation requires a future exact-duration slot and rechecks required participant availability through Scheduling Service.

### AI-assisted request
The default mock parser accepts a documented key/value format rather than unrestricted natural language. The optional OpenAI-compatible provider returns structured data in the same AI-owned DTO format. The caller sends a successful parse response to orchestration. Orchestration creates a REQUESTED meeting and requests suggestions; a user must explicitly choose and confirm a slot.

## Repository layout

~~~text
.
├── shared/                         # shared primitives/configuration
├── user-service/                   # user API, domain, JPA, migration, tests
├── calendar-service/                # calendar and availability API, JPA, tests
├── scheduling-service/              # stateless API and interval algorithms
├── meeting-service/                 # meeting lifecycle API, JPA, tests
├── future/phase7-ai-prototype/       # AI parser and orchestration API
├── legacy-migrations/                # preserved monolith V1–V4 history
├── scripts/start-local.ps1           # Windows local launcher
├── docker-compose.yml
├── pom.xml                           # root Maven reactor
└── README.md
~~~

The notification prototype is excluded from the initial publication and runnable application.

## Prerequisites and local setup

Local execution needs Java 21, Maven 3.9+, PostgreSQL 16 on localhost:5432, the dedicated database smart_meeting_scheduler_it, and a local DB_PASSWORD. The database-backed local application YAML files use separate service schemas in that database. Do not use the development database for automated tests. Integration guards require smart_meeting_scheduler_it and reject smart_meeting_scheduler or JDBC currentSchema overrides.

Create an ignored local environment file, set its database password locally, then build and launch from the repository root in PowerShell:

~~~powershell
Copy-Item .env.example .env
notepad .env
mvn -DskipTests package
.\scripts\start-local.ps1
~~~

The launcher starts the services in dependency order, checks readiness, and stores process IDs and logs under ignored target/ directories. Database services apply their Flyway migrations on startup. The launcher has no separate stop command; it records PIDs in target/local-run/services.json.

## Docker Compose

Docker Desktop with the Linux container engine is required. Compose runs the five services and PostgreSQL 16. The database stays on the internal Compose network and persists in the scheduler_postgres_data named volume. Inter-service URLs use Compose service names. The Compose example selects the mock AI provider.

~~~powershell
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs -f user-service calendar-service scheduling-service meeting-service ai-meeting-service
docker compose down
~~~

docker compose down retains the database volume. Do not add -v unless intentionally deleting its data. PostgreSQL is not published on a host port.

Ollama is outside Compose. From the AI container, the Windows host is reached at host.docker.internal; configure OPENAI_BASE_URL as http://host.docker.internal:11434/v1, select an installed model, and set AI_PROVIDER=openai in local .env. An API key is optional for local compatible servers that do not require authentication. Keep credentials in ignored .env or process environment only.

## API reference

Each service exposes Swagger UI at /swagger-ui.html and OpenAPI JSON at /v3/api-docs, on its own port. Examples use fictional .example email addresses.

### User Service — localhost:8081

| Method | Path | Behavior |
|---|---|---|
| POST | /api/users | Create a user; 201 |
| GET | /api/users/{email} | Get profile and working hours |
| PUT | /api/users/{email}/working-hours | Replace weekly working hours |

Example request:

~~~json
{
  "email": "organizer@example.com",
  "displayName": "Example Organizer",
  "timeZone": "Asia/Kolkata",
  "workingHours": [
    { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "12:00" },
    { "dayOfWeek": "MONDAY", "startTime": "13:00", "endTime": "17:00" }
  ]
}
~~~

### Calendar Service — localhost:8082

| Method | Path | Behavior |
|---|---|---|
| POST | /api/calendar/events | Create BUSY event for an existing user; 201 |
| GET | /api/calendar/events/{eventId} | Retrieve event |
| GET | /api/calendar/users/{email}/events | List events; optional start/end ISO instants filter overlaps |
| DELETE | /api/calendar/events/{eventId} | Soft-cancel event; 204 |
| GET | /api/calendar/users/{email}/availability?start={instant}&end={instant} | Free intervals for instant range |
| GET | /api/calendar/users/{email}/availability/dates?startDate={date}&endDate={date} | Free intervals for inclusive user-local dates |

Example event body:

~~~json
{
  "userEmail": "participant@example.com",
  "title": "Busy period",
  "startAt": "2030-01-07T10:00:00Z",
  "endAt": "2030-01-07T11:00:00Z"
}
~~~

### Scheduling Service — localhost:8083

| Method | Path | Behavior |
|---|---|---|
| POST | /api/scheduling/find-slots | Find and rank candidate slots |

Example request:

~~~json
{
  "organizer": "organizer@example.com",
  "participants": [
    { "email": "required@example.com", "type": "REQUIRED" },
    { "email": "optional@example.com", "type": "OPTIONAL" }
  ],
  "durationMinutes": 30,
  "startDate": "2030-01-07",
  "endDate": "2030-01-09",
  "preferredDate": "2030-01-07",
  "preferredStartTime": "10:00",
  "preferredTimeZone": "Europe/London"
}
~~~

The response contains suggestions with UTC start/end instants, required-availability state, optional-attendance counts, and each attendee's availability and local zoned times. The date range uses the organizer's zone. preferredDate, preferredStartTime, and preferredTimeZone are optional; preferred time is a ranking preference, not a hard constraint.

### Meeting Service — localhost:8084

| Method | Path | Behavior |
|---|---|---|
| POST | /api/meetings | Create REQUESTED meeting; 201 |
| GET | /api/meetings/{meetingId} | Get meeting and participants |
| PUT | /api/meetings/{meetingId} | Update REQUESTED meeting |
| GET | /api/meetings/{meetingId}/participants | List participants and response statuses |
| POST | /api/meetings/{meetingId}/schedule | Request suggestions; body optional |
| POST | /api/meetings/{meetingId}/confirm | Confirm selected exact-duration slot |
| POST | /api/meetings/{meetingId}/cancel | Cancel meeting without deleting it |

Example create body:

~~~json
{
  "title": "Architecture Review",
  "agenda": "Service boundaries and API contracts",
  "durationMinutes": 30,
  "organizerEmail": "organizer@example.com",
  "requiredParticipants": ["required@example.com"],
  "optionalParticipants": ["optional@example.com"],
  "meetingType": "STANDARD"
}
~~~

meetingType accepts STANDARD, VIRTUAL, or IN_PERSON and defaults to STANDARD. Confirm body uses UTC instants:

~~~json
{
  "start": "2030-01-07T14:00:00Z",
  "end": "2030-01-07T14:30:00Z"
}
~~~

The schedule endpoint optionally accepts preferredStartDate, preferredEndDate, preferredStartTime, and preferredTimeZone. Date/time values are local, not UTC. When preferredTimeZone is omitted, the organizer's IANA zone is used. Preference dates must be paired and ordered. Confirmation rechecks availability through Scheduling Service.

### AI Meeting Service — localhost:8085

| Method | Path | Behavior |
|---|---|---|
| POST | /api/ai/meetings/parse | Parse prompt into structured details or ask for clarification |
| POST | /api/ai/meetings/orchestrate | Accept parse response, create meeting, and return suggestions |

The mock parser supports one key: value per nonblank line. Supported keys are title, agenda, durationMinutes, organizerEmail, requiredParticipants, optionalParticipants, preferredStartDate, preferredEndDate, preferredStartTime, and preferredTimeZone. Agenda items are separated by |; emails are comma-separated. It does not perform general natural-language interpretation.

Example request:

~~~json
{
  "prompt": "title: Architecture Review\nagenda: Service boundaries | API contracts\ndurationMinutes: 30\norganizerEmail: organizer@example.com\nrequiredParticipants: required@example.com\npreferredStartDate: 2030-01-07\npreferredEndDate: 2030-01-07\npreferredStartTime: 10:00\npreferredTimeZone: Europe/London"
}
~~~

Parse returns COMPLETE with meetingRequest or NEEDS_CLARIFICATION. Send the successful parse response as the orchestration request body. Orchestration returns SUGGESTIONS_RETURNED with meetingId and suggestions, or NEEDS_CLARIFICATION. It never confirms a slot automatically.

## Database and migrations

| Service | Schema | Flyway migration |
|---|---|---|
| User | user_service | user-service/src/main/resources/db/migration/V1__user_service_schema.sql |
| Calendar | calendar_service | calendar-service/src/main/resources/db/migration/V1__calendar_service_schema.sql |
| Meeting | meeting_service | meeting-service/src/main/resources/db/migration/V1__meeting_service_schema.sql |

The schemas hold users and working hours, calendar events, and meetings and participants respectively. Migrations define UUID keys, constraints, indexes, and timestamps. Event and meeting instants use PostgreSQL TIMESTAMPTZ and Java Instant; Hibernate JDBC time handling is configured for UTC. Working hours are local weekdays/times interpreted through the user's IANA zone.

Service V1 migrations preserve legacy public tables and conditionally copy supported rows when those tables exist. The old monolith V1–V4 files remain under legacy-migrations; current services run their own schema migrations.

Local non-Docker service configuration targets smart_meeting_scheduler_it. Compose has its own PostgreSQL container/database and persistent volume.

## Testing

Run all modules from the repository root:

~~~powershell
mvn test
~~~

At review time, the repository contains 42 Java test source files and 215 test annotations. Coverage includes domain validation, controllers, service behavior, interval algorithms, time zones/DST, PostgreSQL persistence/migrations, transaction rollback, HTTP boundaries, and AI orchestration. Database and HTTP integration tests run in the Maven test lifecycle and require the dedicated smart_meeting_scheduler_it database. Test helpers reject the development database and currentSchema overrides; fixtures use unique records and clean up created rows. Tests do not use Testcontainers.

These counts describe source inventory, not test outcomes. No test suite was run for this README-only update; run mvn test in the intended environment to confirm current pass/fail totals.

## Configuration

Values come from Spring YAML and environment variables. Local database-backed YAML files select smart_meeting_scheduler_it and per-service schemas; Docker profile YAML uses the Compose PostgreSQL hostname and schemas.

| Variable | Used by | Purpose / default |
|---|---|---|
| DB_USERNAME | User, Calendar, Meeting, Compose | Database role; local configuration provides a default |
| DB_PASSWORD | User, Calendar, Meeting, Compose | Local password; required by launcher and Compose |
| POSTGRES_DB | Compose | Docker database name; Compose-specific default |
| USER_SERVICE_PORT | User Service | HTTP port; default 8081 |
| CALENDAR_SERVICE_PORT | Calendar Service | HTTP port; default 8082 |
| SCHEDULING_SERVICE_PORT | Scheduling Service | HTTP port; default 8083 |
| MEETING_SERVICE_PORT | Meeting Service | HTTP port; default 8084 |
| AI_SERVICE_PORT | AI Meeting Service | HTTP port; default 8085 |
| USER_SERVICE_URL | Calendar, Meeting | User Service base URL |
| CALENDAR_SERVICE_URL | Scheduling | Calendar Service base URL |
| SCHEDULING_SERVICE_URL | Meeting | Scheduling Service base URL |
| MEETING_SERVICE_URL | AI Meeting | Meeting Service base URL |
| OUTBOUND_HTTP_CONNECT_TIMEOUT | HTTP clients | Connection timeout; default 2s |
| OUTBOUND_HTTP_READ_TIMEOUT | Calendar, Scheduling, Meeting | Read timeout; default 5s |
| OUTBOUND_HTTP_READ_TIMEOUT | AI Meeting | Read timeout; default 300s |
| SLOT_GRANULARITY_MINUTES | Scheduling | Candidate start granularity; default 15 |
| MEETING_SCHEDULING_SEARCH_WINDOW_DAYS | Meeting | Default search window; default 14 days |
| AI_PROVIDER | AI Meeting | mock by default; openai selects OpenAI-compatible provider |
| OPENAI_BASE_URL | AI Meeting | OpenAI-compatible API base URL |
| OPENAI_MODEL | AI Meeting | Model name; required when provider is openai |
| OPENAI_API_KEY | AI Meeting | Optional for local compatible providers without authentication |

Compose reads the root .env for substitutions. Keep .env local and untracked; .env.example contains placeholders. Never put credentials in tracked files, Dockerfiles, or command history.

## Future improvements and known limitations

- Authentication and authorization are not implemented.
- No email delivery, message broker, API gateway, service discovery, or external calendar synchronization is included.
- Working hours are weekly; holiday calendars and per-date exceptions are not implemented.
- The mock parser supports only its documented key/value format. Real provider behavior depends on an OpenAI-compatible endpoint and configured model.
- Orchestration creates a REQUESTED meeting before asking for suggestions. If the downstream scheduling call fails, the meeting may remain and should be inspected before retrying.
- Service calls are synchronous REST. The project does not claim production-grade security, availability, or deployment hardening.
