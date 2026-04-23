#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="/opt/nemonic"
ARCHIVE_PATH=""
RELEASE_NAME=""
ENV_FILE=""
KEEP_RELEASES="${KEEP_RELEASES:-5}"
APP_NAME="${APP_NAME:-nemonic}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-${APP_NAME}-prod}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-dir)
      BASE_DIR="$2"
      shift 2
      ;;
    --archive)
      ARCHIVE_PATH="$2"
      shift 2
      ;;
    --release)
      RELEASE_NAME="$2"
      shift 2
      ;;
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$ARCHIVE_PATH" || -z "$RELEASE_NAME" ]]; then
  echo "Usage: remote-deploy.sh --archive <release.tar.gz> --release <name> [--base-dir <dir>] [--env-file <path>]" >&2
  exit 1
fi

if [[ -z "$ENV_FILE" ]]; then
  ENV_FILE="$BASE_DIR/shared/.env.prod"
fi

if [[ ! -f "$ARCHIVE_PATH" ]]; then
  echo "Release archive not found: $ARCHIVE_PATH" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi

RELEASES_DIR="$BASE_DIR/releases"
CURRENT_LINK="$BASE_DIR/current"
RELEASE_DIR="$RELEASES_DIR/$RELEASE_NAME"
PREVIOUS_RELEASE="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"

mkdir -p "$RELEASES_DIR"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"
tar -xzf "$ARCHIVE_PATH" -C "$RELEASE_DIR"
ln -sfn "$RELEASE_DIR" "$CURRENT_LINK"

run_release() {
  ENV_FILE="$ENV_FILE" COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" bash "$CURRENT_LINK/deploy/deploy.sh"
  ENV_FILE="$ENV_FILE" COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" bash "$CURRENT_LINK/deploy/smoke-test.sh"
}

rollback() {
  if [[ -n "$PREVIOUS_RELEASE" && -d "$PREVIOUS_RELEASE" ]]; then
    echo "Deployment failed. Rolling back to $PREVIOUS_RELEASE" >&2
    ln -sfn "$PREVIOUS_RELEASE" "$CURRENT_LINK"
    ENV_FILE="$ENV_FILE" COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" bash "$CURRENT_LINK/deploy/deploy.sh"
  fi
}

if ! run_release; then
  rollback
  exit 1
fi

find "$RELEASES_DIR" -mindepth 1 -maxdepth 1 -type d | sort | head -n -"${KEEP_RELEASES}" 2>/dev/null | xargs -r rm -rf
rm -f "$ARCHIVE_PATH"

echo "Deployment completed successfully: $RELEASE_NAME"
