#!/usr/bin/env bash
# Writes the two local secret files. Both are gitignored.
#   ops/langfuse/.env   secrets for the Langfuse containers
#   config/agent.env    the keys the agent process reads
# Run from java-learning-project. It never overwrites an existing file.
set -euo pipefail

lf_env=ops/langfuse/.env
agent_env=config/agent.env

if [[ -e $lf_env || -e $agent_env ]]; then
  echo "refusing: $lf_env or $agent_env already exists. Delete it first." >&2
  exit 1
fi

# Hex only, so the values are safe in a URL (DATABASE_URL) and in sh -c.
rand() { openssl rand -hex 24; }

pk="pk-lf-$(openssl rand -hex 16)"
sk="sk-lf-$(openssl rand -hex 16)"
password="$(rand)"

umask 077
cat >"$lf_env" <<EOF
NEXTAUTH_SECRET=$(openssl rand -base64 32)
SALT=$(openssl rand -base64 32)
ENCRYPTION_KEY=$(openssl rand -hex 32)
POSTGRES_PASSWORD=$(rand)
CLICKHOUSE_PASSWORD=$(rand)
REDIS_AUTH=$(rand)
MINIO_ROOT_PASSWORD=$(rand)
LANGFUSE_INIT_USER_EMAIL=admin@clickstream.local
LANGFUSE_INIT_USER_PASSWORD=$password
LANGFUSE_PUBLIC_KEY=$pk
LANGFUSE_SECRET_KEY=$sk
EOF

# The agent reads these from its environment. Load with:
#   set -a; . config/agent.env; set +a
cat >"$agent_env" <<EOF
# Paste your Gemini key here. Empty means the local template writer.
GEMINI_API_KEY=
LANGFUSE_HOST=http://127.0.0.1:3300
LANGFUSE_PUBLIC_KEY=$pk
LANGFUSE_SECRET_KEY=$sk
EOF

echo "wrote $lf_env and $agent_env"
echo "Langfuse login: admin@clickstream.local / $password"
