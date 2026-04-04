#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.deploy.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE"
  echo "Create it from .deploy.env.example before running deploy.sh"
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1"
    exit 1
  fi
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required config: $name"
    exit 1
  fi
}

require_command ssh
require_command scp
require_command sshpass
require_command mvn
require_command npm

require_env DEPLOY_HOST
require_env DEPLOY_PORT
require_env DEPLOY_USER
require_env DEPLOY_PASSWORD
require_env REMOTE_BACKEND_DIR
require_env REMOTE_FRONTEND_DIR
require_env REMOTE_BACKEND_START_SCRIPT
require_env BACKEND_MODULE
require_env BACKEND_JAR_NAME
require_env SPRING_PROFILE

if [[ -n "${DEPLOY_JAVA_HOME:-}" ]]; then
  export JAVA_HOME="$DEPLOY_JAVA_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

REMOTE_TARGET="$DEPLOY_USER@$DEPLOY_HOST"
LOCAL_BACKEND_JAR="$SCRIPT_DIR/$BACKEND_MODULE/target/$BACKEND_JAR_NAME"
LOCAL_FRONTEND_DIR="$SCRIPT_DIR/temu-upin-frontend"
LOCAL_FRONTEND_DIST="$LOCAL_FRONTEND_DIR/dist"
SSH_OPTS=(-p "$DEPLOY_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR)
SCP_OPTS=(-P "$DEPLOY_PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR)

ssh_run() {
  SSHPASS="$DEPLOY_PASSWORD" sshpass -e ssh "${SSH_OPTS[@]}" "$REMOTE_TARGET" "$@"
}

scp_upload() {
  local source_path="$1"
  local target_path="$2"
  SSHPASS="$DEPLOY_PASSWORD" sshpass -e scp "${SCP_OPTS[@]}" "$source_path" "$REMOTE_TARGET:$target_path"
}

scp_upload_dir() {
  local source_dir="$1"
  local target_dir="$2"
  SSHPASS="$DEPLOY_PASSWORD" sshpass -e scp -r "${SCP_OPTS[@]}" "$source_dir/." "$REMOTE_TARGET:$target_dir/"
}

build_backend() {
  echo "==> Building backend"
  cd "$SCRIPT_DIR"
  mvn -pl "$BACKEND_MODULE" -am clean package -DskipTests

  if [[ ! -f "$LOCAL_BACKEND_JAR" ]]; then
    echo "Backend jar not found: $LOCAL_BACKEND_JAR"
    exit 1
  fi
}

build_frontend() {
  echo "==> Building frontend"
  cd "$LOCAL_FRONTEND_DIR"

  if [[ ! -d node_modules ]]; then
    npm install
  fi

  npm run build

  if [[ ! -f "$LOCAL_FRONTEND_DIST/index.html" ]]; then
    echo "Frontend dist not found: $LOCAL_FRONTEND_DIST"
    exit 1
  fi
}

deploy_backend() {
  echo "==> Uploading backend jar"
  scp_upload "$LOCAL_BACKEND_JAR" "$REMOTE_BACKEND_DIR/$BACKEND_JAR_NAME"

  echo "==> Stopping old backend process"
  ssh_run "cd '$REMOTE_BACKEND_DIR' && pids=\$(pgrep -f '[j]ava -jar $BACKEND_JAR_NAME' || true) && if [ -n \"\$pids\" ]; then kill -9 \$pids; fi"

  echo "==> Starting backend"
  ssh_run "cd '$REMOTE_BACKEND_DIR' && bash '$REMOTE_BACKEND_START_SCRIPT'"

  echo "==> Verifying backend"
  ssh_run "sleep 3 && cd '$REMOTE_BACKEND_DIR' && if pgrep -af '[j]ava -jar $BACKEND_JAR_NAME' >/dev/null; then pgrep -af '[j]ava -jar $BACKEND_JAR_NAME'; else tail -n 80 app-$SPRING_PROFILE.log; exit 1; fi"
}

deploy_frontend() {
  echo "==> Preparing frontend target directory"
  ssh_run "mkdir -p '$REMOTE_FRONTEND_DIR' && find '$REMOTE_FRONTEND_DIR' -mindepth 1 -maxdepth 1 -exec rm -rf {} +"

  echo "==> Uploading frontend dist"
  scp_upload_dir "$LOCAL_FRONTEND_DIST" "$REMOTE_FRONTEND_DIR"

  echo "==> Verifying frontend files"
  ssh_run "cd '$REMOTE_FRONTEND_DIR' && ls -la"
}

usage() {
  echo "Usage: ./deploy.sh [all|backend|frontend]"
}

main() {
  local mode="${1:-all}"

  case "$mode" in
    all)
      build_backend
      deploy_backend
      build_frontend
      deploy_frontend
      ;;
    backend)
      build_backend
      deploy_backend
      ;;
    frontend)
      build_frontend
      deploy_frontend
      ;;
    *)
      usage
      exit 1
      ;;
  esac

  echo "==> Deploy finished"
}

main "$@"