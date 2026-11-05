# Ingress agent

The edge calls the agent when a hardcoded threshold on the gRPC hop to the ingest server is crossed. The agent writes a report and stores it. The report UI can open that report and ask a follow-up. Follow-up questions stay on the stored snapshot. They do not open a second report.

Diagrams:

- [Components](diagrams/ingress-agent-components.svg)
- [Opening a report](diagrams/ingress-agent-open-report.svg)
- [Follow-up](diagrams/ingress-agent-follow-up.svg)
- [One signal](diagrams/ingress-agent-signal.svg)
- [Classes](diagrams/ingress-agent-classes.svg)
- [Tables](diagrams/ingress-agent-schema.svg)

## What exists today

A collector posts events to the edge. The edge validates them, returns HTTP 202, and puts the batch on `AcceptedEventQueue`. `IngestDispatcher` is one background thread. It calls `GrpcIngestClient.accept`, which streams the batch to `IngestGrpcService` and blocks until the server acks or the 5 second deadline fires. The ack means the rows are in the server's memory buffer. Doris is later.

The edge already publishes `clickstream_ingest_queue_depth` on `127.0.0.1:9464`. That gauge is the number of batches waiting for the dispatcher. There is no metric for the RPC itself: no latency, no status code, no dropped batch. A threshold cannot see the hop until those exist.

Prometheus, Loki, and Grafana already scrape the edge and the server. They stay as they are. This agent is a second kind of record: a written explanation of one crossing, plus the questions asked about it.

## Architecture

The agent is its own process and its own Maven module. The edge does not call a model, and it does not open the report database. The dispatcher thread is the hop. If that thread waits on a model, the queue grows, which is the failure we are trying to notice.

The edge counts what the dispatcher already does, then compares those counts to hardcoded constants in `IngressTriggers`. There is no poll. The agent process does not wake on a timer, and it does not read `/metrics` to decide anything. `HopGate` runs only because a batch was just sent, dropped, or is sitting in the queue. When a constant is crossed on a rising edge, `AgentNotifier` posts that snapshot to the agent. The browser talks only to the agent, on `127.0.0.1:8095`. Langfuse receives finished spans after the report is stored. A dead Langfuse, a down agent, or a slow model must not stall the dispatcher. The notifier posts on its own thread.

Reports go in a local SQLite file, `data/ingress-reports.db`. They are not clickstream rows, so they do not go to Doris. Doris may be the thing that is sick. The report has to be readable when Doris is not.

There is no button that creates a report. `HopGate` is the only thing that asks for a new one. The UI lists them, shows one, and appends a follow-up to that one.

### The hardcoded triggers

The constants live in the edge, in `IngressTriggers`. They are not properties, and the agent does not configure them.

| Constant | Value | Meaning |
| --- | --- | --- |
| `QUEUE_DEPTH` | 100 | Batches waiting for the dispatcher. |
| `ERROR_RATIO` | 0.25 | Failed attempts divided by attempts in the ring. |
| `MIN_ATTEMPTS` | 5 | The ratio and the mean stay quiet until the ring holds at least this many results. |
| `WINDOW_ATTEMPTS` | 20 | How many recent Accept results the ring keeps. This is a count of RPCs, not a clock. |
| `DROPPED_BATCHES` | 1 | One batch the dispatcher will not send again. |
| `MEAN_SECONDS` | 1 | Mean Accept time across the ring. The RPC deadline is 5 seconds, so 1 second is an early mark. |

Each signal has its own copy of the state machine in [ingress-agent-signal](diagrams/ingress-agent-signal.svg). A rise opens at most one report. Staying hot does not open another. The signal has to fall below its constant and rise again. Several signals can rise on the same dispatcher call. That is one snapshot and one report. The `triggers` list holds every reason.

The ring is the last 20 finished Accept calls. A new result pushes the oldest one out. Queue depth does not use the ring. It is the queue size at that moment. A drop does not use the ring either. The drop constant is crossed when this batch is abandoned, and the signal falls again after a later batch is accepted.

Prometheus still scrapes the edge for Grafana. Those series are not an input to the trigger. The gate already has the numbers, because it just recorded them.

### What the report is allowed to claim

The snapshot is the only evidence. It can say the queue is deep, the RPC is failing, batches were dropped, or the mean ack time is high. It cannot say which tenant, which event, or whether Doris is the cause. The server's stream-load histogram is a different hop and is out of this design. The writer is told that in the prompt, and the local writer says it in the text.

## Low-level design

Package `com.learning.clickstream.agent` for the report process. The trigger lives in the edge, next to the dispatcher.

### Edge

`IngestDispatcher.sendWithRetry` times each call to `client.accept`, updates the Prometheus series, then tells `HopGate`.

- Success: `metrics.recordForward(startedNanos, true, "OK")`, then `gate.onForward(true, "OK", seconds, queue.size())`.
- Any failure: `metrics.recordForward(startedNanos, false, statusCodeName)`, then `gate.onForward(false, code, seconds, queue.size())`, then the retry or drop logic that already exists.
- Drop: `metrics.recordDropped()` once, on the path that already logs "dropping batch", then `gate.onDrop(queue.size())`. That path is an invalid or unauthenticated batch, the third failed attempt, or an interrupted sleep.

`EdgeMetrics` adds the series Grafana can scrape. The gate does not read them back.

- `clickstream_grpc_forward_seconds`, a classic histogram with the same buckets as `clickstream_stream_load_seconds`.
- `clickstream_grpc_forward_total`, labels `result` (`ok` or `error`) and `code` (the gRPC status name).
- `clickstream_grpc_batches_dropped_total`.

What each of those names counts is written out in [ingress-hop-metrics.md](ingress-hop-metrics.md).

The names use the `_total` suffix in the builder, matching `clickstream_events_accepted_total`, which Grafana already queries.

`HopGate` keeps the ring of the last `WINDOW_ATTEMPTS` results and the hot flag for each signal. `onForward` and `onDrop` return a snapshot only when at least one signal rose. `AgentNotifier.submit` queues that snapshot and returns. A single daemon thread posts it to `POST /api/incidents` with a 2 second timeout. If the agent is down, the thread logs the miss and continues. It does not retry, and the dispatcher has already moved on.

### Snapshot

`Snapshot` is the body of that POST, and it is what we store:

- `occurredAt`
- `queueDepth`
- `attempts`, `failures`, `dropped` in the ring (drops are 0 or 1 for the call that just abandoned a batch)
- `meanSeconds` across the ring, or 0 when the ring is empty
- `errorsByCode` for the failures still in the ring
- `reasons`, the sentences the gate produced
- the constant values that were crossed

Reason text is fixed, so both writers quote the same sentence. Examples:

- `queue depth 150 crossed 100 (batches waiting to be sent to the ingest server)`
- `gRPC error ratio 0.40 crossed 0.25 (8 failed of the last 20 attempts)`
- `dropped batches crossed 1 (this batch will not be sent again)`
- `mean Accept time 1.40s crossed 1s over the last 20 attempts`

### Writing

`IncidentWriter` has three methods: `modelName()`, `writeReport(snapshot)`, and `answer(query)`.

`LocalIncidentWriter` does not call a model. The report restates the reasons, then the queue depth and the ring counts, then two limits: the edge has already returned 202, and this snapshot cannot see Doris or a single tenant. A follow-up answers from that stored snapshot and the messages already on the report. It does not go back to the edge.

`LlmIncidentWriter` sends the same facts to Gemini through LangChain4j 1.22.0 (`GoogleAiGeminiChatModel`, artifact `langchain4j-google-ai-gemini`). The system prompt says to use only those numbers and not to invent a cause. The default model name is `gemini-2.5-flash`. `agent.model.base.url` is optional. Leave it empty and the client uses Google's Gemini endpoint. The API key is read from `GEMINI_API_KEY`, then `GEMINI_AI_KEY`. It is not put in the properties file.

`IncidentService` catches a failure from the primary writer and stores the local text with `model` set to `local`. If the primary writer already is the local writer, the exception propagates. The body stored for one turn is capped at 16_000 characters.

`FollowUpQuery` carries the stored snapshot, the report text, the message list (last 20), and the new question. The snapshot is the evidence the first answer used: queue depth, the ring counts, the mean time, the status codes, and the reason sentences. It is stored on the report row, so the follow-up reads it from SQLite and puts that same JSON in the prompt again. The chat history alone is the prose, and a later turn can drift off those numbers. Sending the snapshot every time keeps Gemini on the same facts. A follow-up never calls `open`, and it does not open `logs/edge.json` or Loki. Those lines are one request each, and the file rolls, so they are not the record of this crossing.

### Storage

SQLite, one connection per call, and every `ReportStore` method is synchronized. The volume is one write per crossing, plus a chat turn. The lock is enough. WAL is not required.

`reports`

| Column | Meaning |
| --- | --- |
| `id` | UUID text, primary key. Also the Langfuse session id. |
| `created_at` | ISO-8601. |
| `triggers` | JSON array of the reason sentences. |
| `snapshot_json` | The `Snapshot` record. |
| `body` | The report text. Also copied into the first assistant message so the UI can render one transcript. |
| `model` | `local`, or the configured model name. |
| `trace_id` | 32 lowercase hex characters. |
| `root_span_id` | 16 lowercase hex characters. Follow-up spans use this as their parent. |

`messages`

| Column | Meaning |
| --- | --- |
| `id` | Integer primary key. |
| `report_id` | The report. |
| `role` | `user` or `assistant`. |
| `content` | The turn. |
| `created_at` | ISO-8601. |

Insert of the report and its first assistant message is one transaction. List returns newest first and does not load messages. The preview is the first line of `body`, cut at 140 characters. `find` returns the report plus messages ordered by `id`. An unknown id is an empty result, and the servlet turns that into 404.

### HTTP

Loopback only. No auth, same posture as the Grafana port on this machine.

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/` | The one page. |
| `POST` | `/api/incidents` | Body is the `Snapshot` from the edge. This is the only way a report is created. |
| `GET` | `/api/reports` | `[{id, createdAt, triggers, model, preview}]` |
| `GET` | `/api/reports/{id}` | The report, the snapshot, and `messages`. |
| `POST` | `/api/reports/{id}/messages` | Body `{"question":"..."}`. Empty or over 4_000 characters is 400. Unknown id is 404. Response is the new assistant message. |

The page has a list on the left and the open report on the right. The list loads when you open the page. After a successful follow-up, the page appends the question and the answer it just received. Report text is set with `textContent`. The snapshot numbers are rendered from `snapshot_json` above the prose: queue depth, attempts, failures, dropped, mean seconds, and errors by status.

### Langfuse

Spans go to `{host}/api/public/otel/v1/traces` as OTLP JSON. The request uses Basic auth with the project public key and secret key, and the header `x-langfuse-ingestion-version: 4`. The legacy `/api/public/ingestion` batch API is not used. Langfuse Cloud sunsets it on 16 November 2026.

Keys come from `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`, and `LANGFUSE_HOST`. If any of the three is blank, export is a no-op.

One incident is one trace. The trace name `ingress-incident` and the session id (the report id) are copied onto every span. The root span has `langfuse.observation.type` `agent`, and its input and output are the snapshot the edge posted and the report. A child of type `generation`, named `write-report`, holds the prompt and the body, plus `langfuse.observation.model.name`. A follow-up exports a new generation span, named `follow-up`, with the same trace id and the stored root span id as its parent. The root span is not exported again.

Export runs after the SQLite commit. The HTTP client timeout is 2 seconds. A non-2xx response is logged. The report remains.

### Langfuse in Docker

Langfuse is its own Compose file, `ops/langfuse/docker-compose.yml`. It does not join `ops/observability/docker-compose.yml`. That file uses the host network so Prometheus can scrape `127.0.0.1`. Langfuse's containers find each other by name (`postgres`, `redis`, `clickhouse`, `minio`), which host networking would break. It also wants ports this machine already uses: Grafana is `3000`, Loki is `3100`, Prometheus is `9090`.

The stack is six containers on a private bridge network. Only the UI is published, and only on loopback.

| Container | Role | Host port |
| --- | --- | --- |
| `langfuse-web` | UI and the OTLP ingest endpoint | `127.0.0.1:3300` |
| `langfuse-worker` | Writes traces into ClickHouse | none |
| `postgres` | Users, projects, API keys | none |
| `clickhouse` | The traces | none |
| `redis` | The worker queue | none |
| `minio` | Blob store for large fields | `127.0.0.1:9190`, because the browser uploads through it |

`NEXTAUTH_URL` is `http://127.0.0.1:3300`. The S3 upload URL the browser uses is `http://127.0.0.1:9190`. The worker still talks to MinIO at `http://minio:9000` inside the compose network.

Secrets are generated once and kept in `ops/langfuse/.env`, which is gitignored. A committed `.env.example` lists the names and leaves the values empty. The three Langfuse secrets are `NEXTAUTH_SECRET` and `SALT` (`openssl rand -base64 32`) and `ENCRYPTION_KEY` (`openssl rand -hex 32`). Postgres, Redis, ClickHouse, and MinIO get their own passwords in that same file.

After `docker compose up -d`, open `http://127.0.0.1:3300`, create the one local user, create a project named `clickstream-ingress`, and create a project API key. Put these in the environment of the agent process, not in git:

```text
LANGFUSE_HOST=http://127.0.0.1:3300
LANGFUSE_PUBLIC_KEY=pk-lf-...
LANGFUSE_SECRET_KEY=sk-lf-...
```

The agent posts to `http://127.0.0.1:3300/api/public/otel/v1/traces`. If those three variables are blank, it sends nothing, so the report UI still works with the stack stopped. Start this compose only when you want to look at a trace. ClickHouse is the heavy part.

### Config

`config/agent.properties` holds the non-secret values. The loader uses the file, then replaces a value when the matching environment variable is set.

| Key | Default |
| --- | --- |
| `agent.http.port` | `8095` |
| `agent.db.path` | `data/ingress-reports.db` |
| `agent.model.base.url` | empty |
| `agent.model.name` | `gemini-2.5-flash` |
| `agent.model.timeout.seconds` | `30` |

The trigger numbers are not in this file. They are the constants in `IngressTriggers`. The edge posts to `http://127.0.0.1:8095/api/incidents`. That URL is a constant on `AgentNotifier`, same host and port as the UI.

`data/` is gitignored when this is built. The database is created on startup, including the parent directory.

### Boot

From `java-learning-project`:

```text
mvn -pl agent -am exec:java -Dexec.mainClass=com.learning.clickstream.agent.AgentMain
```

The agent can start with an empty list. A report appears only after the edge crosses a constant and the POST lands. If the agent is down at that moment, the edge logs the miss and does not retry. The UI is `http://127.0.0.1:8095/`.

### Out of scope

A timer that polls `/metrics` or Prometheus and then decides to call the agent. Doris queries, the server stream-load histogram, Prometheus alert rules, Langfuse Cloud, prompt datasets, and scores. Auth on the report UI. Editing a threshold from the page. A report that is not tied to a crossing.

## Implementation plan

Each phase ends with the test named in it. The next phase does not start on a red test. No model key is required for any test.

1. **Count the hop, in the edge module only.** Add the three series and the two methods. Call them from `IngestDispatcher`. Test: a fake `IngestClient` that throws `UNAVAILABLE` makes the dispatcher drop one batch, and `GET /metrics` contains `clickstream_grpc_forward_total` with `UNAVAILABLE`, plus `clickstream_grpc_batches_dropped_total`. A second test records one success and one failure and asserts both `result` labels. This phase does not add the `agent` module. The parent POM stays as it is.

2. **Hardcoded gate.** Add `IngressTriggers`, `HopGate`, and `Snapshot` in the edge. No agent process yet. `HopGate` returns a snapshot to the test instead of posting. Tests cover a queue of 100, a ratio that stays quiet under 5 attempts, a ratio that fires on the 20-result ring, one drop, a mean over 1 second, a second call that stays hot and does not fire, a fall below the constant followed by a new rise, and two signals on one call producing one snapshot.

3. **Store the report.** Add the `agent` module and `ReportStore` against a temp file. Test insert, newest-first list, append, and a missing id. `POST /api/incidents` is not in this phase yet.

4. **Write the text.** `LocalIncidentWriter` and `IncidentService` with an exporter that records calls and does not open a socket. Test that the stored body contains the queue number and the reason sentence, and that a follow-up stores the question and an answer that quotes the stored snapshot.

5. **Serve the page.** `AgentServer`, the servlets, and `ui/index.html`. `POST /api/incidents` with a snapshot opens one report. Then `GET /api/reports`, `GET /api/reports/{id}`, `POST` a question, and assert 400 and 404. After that, load the page in a browser, open the report, and send a follow-up. Wire `AgentNotifier` in the edge so a real crossing posts to that path, and test it against a local HTTP server that records one POST.

6. **Export the trace.** `LangfuseExporter` against a local HTTP server. Assert the v4 header, Basic auth, one agent span, one generation span, the trace id, and the session id. Assert that blank keys send nothing, and that a 500 from the sink still leaves the row in SQLite.

7. **Call a model when a key exists.** `LlmIncidentWriter`, used by `AgentMain` only when a key is present. Test the fallback with a writer that throws: the stored `model` is `local` and the body is the local report. Do not call a live model from the test suite.

8. **Boot it.** `AgentConfig`, the loader, `config/agent.properties`, `logback.xml`, `data/` in `.gitignore`, and `AgentMain`. Parent POM gains the module, `sqlite-jdbc` 3.47.2.0, and `langchain4j-google-ai-gemini` 1.22.0. Run the edge, run the agent, push the queue or the RPC over a threshold, and confirm one row and one trace.
