# Clickstream

A small Java pipeline for learning what happens to a click event after it is accepted, and what you write down when the hop that carries it gets sick.

Events enter at the edge. The edge checks them, returns 202, and puts the batch on a queue. One background thread sends the batch to the ingest server over gRPC and waits for an ack. That ack means the rows are in the server's memory. It does not mean they are safely stored further downstream.

Prometheus, Loki, and Grafana already show the queue depth and the logs. A gauge can tell you that a threshold was crossed. It cannot tell you, in one place, which numbers crossed and what the report is allowed to claim. That is what the agent is for.

## Why the agent is a separate process

The dispatcher thread is the hop. If that thread waits on a model, the queue grows, which is the failure you are trying to notice. The edge only counts what it just did and compares those counts to hardcoded limits. When one of them rises, it posts a snapshot to the agent on another thread and moves on. A down agent, a slow model, or a dead Langfuse costs a log line. It does not stall ingest.

The snapshot is the only evidence. It can say the queue is deep, the RPC is failing, a batch was dropped, or the mean ack time is high. It cannot say which user, or that the database further downstream is the cause. The report is stored in a local SQLite file so you can still read it when that database is the thing that is down.

## What is in the tree

| Module | Role |
| --- | --- |
| `java-learning-project/proto` | The gRPC contract between the edge and the server. |
| `java-learning-project/edge` | HTTP ingest, the queue, and the hop gate. |
| `java-learning-project/server` | The ingest service that acks a batch. |
| `java-learning-project/agent` | Writes the report, serves it on loopback, and exports a trace. |
| `java-learning-project/ops/observability` | Prometheus, Loki, and Grafana. |
| `java-learning-project/ops/langfuse` | A local Langfuse for traces, prompts, and the judge. |

The longer design notes are [docs/ingress-agent.md](docs/ingress-agent.md), [docs/ingress-hop-metrics.md](docs/ingress-hop-metrics.md), and [docs/langfuse-evals.md](docs/langfuse-evals.md).

## What happens to one crossing

1. The edge records the Accept call: how long it took, whether it failed, and how deep the queue is.
2. `HopGate` opens at most one report when a limit is crossed on the way up. Staying hot does not open another.
3. The agent writes the text. With no Cloud project and no API key, a local template does it. Otherwise Gemini does, and a guardrail checks the result before it is stored.
4. The number check rejects a figure that is not in the snapshot. The topic check refuses a follow-up that is not about this report. A failed check is retried once, then the local text is stored.
5. The page on `127.0.0.1:8095` lists the report and takes a follow-up. The follow-up is answered from the stored snapshot, not from a fresh read of the edge.
6. After the row is stored, the agent posts the trace to Langfuse. Langfuse can score that stored text later. The score does not rewrite the report.

## Configuration

Secrets are not in git. `config/agent.properties` holds ports, the model name, and the Cloud project id. API keys are read from the environment:

- `GEMINI_API_KEY`, only if `agent.gcp.project` is empty. A set project uses Application Default Credentials against the Gemini platform.
- `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, and `LANGFUSE_SECRET_KEY`. If any of them is blank, no trace is sent and the report is still stored.

`ops/langfuse/make-env.sh` writes `ops/langfuse/.env` and `config/agent.env`. Both files are gitignored. Put a Gemini key in `config/agent.env` yourself. The script leaves that line empty.

## Run

From `java-learning-project`:

```text
ops/langfuse/make-env.sh
docker compose -f ops/langfuse/docker-compose.yml up -d
set -a; . config/agent.env; set +a
mvn -pl agent install -DskipTests
mvn -pl agent exec:java
```

The report page is `http://127.0.0.1:8095/`. Langfuse is `http://127.0.0.1:3300`. A report appears only after the edge crosses a limit and the post lands.
