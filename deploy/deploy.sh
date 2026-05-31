#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

if [ ! -f app.jar ]; then
  echo "Missing deploy/app.jar"
  echo "Build the backend jar and copy it to deploy/app.jar first."
  exit 1
fi

if [ ! -f web/dist/index.html ]; then
  echo "Missing deploy/web/dist/index.html"
  echo "Build the frontend and copy VoiceCal/dist to deploy/web/dist first."
  exit 1
fi

if [ ! -f .env ]; then
  echo "Missing deploy/.env"
  echo "Run: cp .env.example .env"
  echo "Then fill in the server configuration."
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  compose() {
    docker compose "$@"
  }
elif command -v docker-compose >/dev/null 2>&1; then
  compose() {
    docker-compose "$@"
  }
else
  echo "Docker Compose is required. Install Docker Compose v2 or docker-compose."
  exit 1
fi

compose up -d --build
compose ps

echo "VoiceCal frontend and backend are starting."
echo "Run 'docker compose logs -f' to follow startup logs."
