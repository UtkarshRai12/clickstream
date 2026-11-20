#!/usr/bin/env bash
# Posts a stream of fake click events to the edge.
# The edge must already be listening. This is normal traffic: it does not
# by itself cross a trigger. A trigger fires when the gRPC hop to the server
# fails or the queue grows.
#
# From java-learning-project:
#   ops/send-dummy-events.sh            # 20 events, 0.3s apart
#   ops/send-dummy-events.sh 100 0.1    # count, then pause in seconds
#
# EDGE_URL and API_KEY override the defaults.
set -euo pipefail

count="${1:-20}"
pause="${2:-0.3}"
edge="${EDGE_URL:-http://127.0.0.1:8080}"
key="${API_KEY:-ingest-dev-secret}"

if ! [[ "$count" =~ ^[1-9][0-9]*$ ]]; then
  echo "count must be a positive whole number, got: $count" >&2
  exit 1
fi

token="$(curl -sf -X POST -H "X-Api-Key: $key" "$edge/v1/token" \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')"
if [[ -z "$token" || "$token" == *"{"* ]]; then
  echo "could not get a token from $edge/v1/token" >&2
  exit 1
fi

paths=(/home /pricing /docs /signup)
types=(page_view click)

for i in $(seq 1 "$count"); do
  # event_id is capped at 64 characters by the edge.
  id="$(printf 'dummy-%s-%s' "$(date +%s)" "$i")"
  path="${paths[$((i % 4))]}"
  type="${types[$((i % 2))]}"
  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  body="$(printf '{"events":[{"event_id":"%s","user_id":"ada","event_type":"%s","occurred_at":"%s","properties":{"path":"%s"}}]}' \
    "$id" "$type" "$now" "$path")"
  code="$(curl -s -o /tmp/send-dummy-events.body -w '%{http_code}' \
    -X POST \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -d "$body" \
    "$edge/v1/events:batch")"
  printf '%s %s %s\n' "$i" "$code" "$(cat /tmp/send-dummy-events.body)"
  if [[ "$i" -lt "$count" ]]; then
    sleep "$pause"
  fi
done
