# Ingress hop metrics

These three names are Prometheus series on the edge. They describe the gRPC Accept call from `IngestDispatcher` to the ingest server. They are not Java fields, and they are not the Langfuse API keys.

The edge publishes them on `http://127.0.0.1:9464/metrics` so Grafana can draw them. `HopGate` does not read this page back. It already has the same numbers, because the dispatcher just recorded them.

A batch can be tried up to three times. Each try updates the first two series. The third series moves only when the dispatcher gives up on the batch.

## clickstream_grpc_forward_seconds

How long one Accept call took, in seconds. The clock starts just before `client.accept` and stops when that call returns or throws. The time includes waiting for the server ack. The server's deadline for that call is 5 seconds.

This is a classic histogram, the same shape as `clickstream_stream_load_seconds` on the ingest server. Prometheus scrapes three families from it:

| Scraped name | Meaning |
| --- | --- |
| `clickstream_grpc_forward_seconds_bucket{le="..."}` | How many calls finished in that many seconds or less. |
| `clickstream_grpc_forward_seconds_sum` | Total seconds across every call. |
| `clickstream_grpc_forward_seconds_count` | How many calls were timed. |

The bucket upper bounds, in seconds, are `0.01`, `0.05`, `0.1`, `0.25`, `0.5`, `1`, `2.5`, `5`, and `10`. A call that took 0.3 seconds increments every bucket whose bound is at least 0.3, and also adds 0.3 to `_sum` and 1 to `_count`.

The mean time for a set of calls is `_sum` divided by `_count` for those calls. The hardcoded trigger `MEAN_SECONDS` is 1. It compares that mean over the last 20 calls with 1 second.

A failed call is timed too. A timeout that burns the whole deadline shows up near the 5 second bucket.

## clickstream_grpc_forward_total

How many Accept calls finished. One line per pair of labels. The count never goes down except when the edge process restarts.

| Label | Values | Meaning |
| --- | --- | --- |
| `result` | `ok` or `error` | `ok` means the server acked the batch. `error` means the call threw. |
| `code` | a gRPC status name | `OK` on success. On failure, the status the dispatcher saw, such as `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `INVALID_ARGUMENT`, `UNAUTHENTICATED`, or `CANCELLED`. |

Two example lines:

```text
clickstream_grpc_forward_total{result="ok",code="OK"} 12
clickstream_grpc_forward_total{result="error",code="UNAVAILABLE"} 8
```

A retry counts again. A batch that fails twice and then succeeds adds 2 to an error line and 1 to the `ok`/`OK` line, and it adds 3 observations to the histogram. The HTTP collector already got its 202 before any of this. This counter is the hop after that 202.

`ERROR_RATIO` uses these counts over the last 20 calls: error calls divided by all calls. It stays quiet until at least 5 calls are in that ring. The ratio that opens a report is 0.25.

## clickstream_grpc_batches_dropped_total

How many batches the dispatcher abandoned. Those events are not sent again. The counter goes up by 1 per batch, not per try and not per event inside the batch.

A batch is dropped in three cases:

- The server answered `INVALID_ARGUMENT` or `UNAUTHENTICATED`. Sending it again would fail the same way.
- Three tries all failed for a reason the dispatcher is willing to retry, such as `UNAVAILABLE` or `DEADLINE_EXCEEDED`.
- The wait between tries was interrupted, so the dispatcher stopped.

The failed tries are already on `clickstream_grpc_forward_total`. This series is the extra fact that the batch will not be retried. `DROPPED_BATCHES` is 1, so a single abandoned batch can open a report.

A successful ack does not touch this counter. A full HTTP queue on the edge is also not this counter. That case is rejected with HTTP 503 before a batch exists for the dispatcher.
