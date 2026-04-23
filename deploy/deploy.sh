#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/deploy/.env.prod}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nemonic-prod}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  echo "Copy deploy/.env.prod.example to deploy/.env.prod and fill in secrets." >&2
  exit 1
fi

docker compose -p "$COMPOSE_PROJECT_NAME" --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.prod.yml" up -d --build
docker compose -p "$COMPOSE_PROJECT_NAME" --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.prod.yml" ps
