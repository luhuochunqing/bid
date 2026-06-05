#!/usr/bin/env bash
# Input: repository source tree, installed toolchains, and release build environment variables
# Output: versioned release archive containing frontend assets, backend jar, and metadata
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
RELEASE_ID="${RELEASE_ID:-$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || date +%Y%m%d-%H%M%S)}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/.release/$RELEASE_ID}"
ARCHIVE_PATH="${ARCHIVE_PATH:-$ROOT_DIR/.release/xiyu-bid-release-${RELEASE_ID}.tar.gz}"
API_BASE_URL="${VITE_API_BASE_URL:-${PRODUCTION_API_BASE_URL:-http://127.0.0.1:18080}}"

mkdir -p "$OUTPUT_DIR/frontend" "$OUTPUT_DIR/backend" "$(dirname "$ARCHIVE_PATH")"

printf '==> Building frontend release assets\n'
cd "$ROOT_DIR"
VITE_API_MODE=api VITE_API_BASE_URL="$API_BASE_URL" npm run build:api

printf '\n==> Packaging backend jar\n'
cd "$BACKEND_DIR"
mvn -DskipTests package

JAR_PATH="$(find "$BACKEND_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name '*original*.jar' | sort | head -n 1)"
if [[ -z "${JAR_PATH:-}" ]]; then
  printf 'No backend jar produced under %s/target\n' "$BACKEND_DIR" >&2
  exit 1
fi

rm -rf "$OUTPUT_DIR/frontend" "$OUTPUT_DIR/backend"
mkdir -p "$OUTPUT_DIR/frontend" "$OUTPUT_DIR/backend"
cp -R "$ROOT_DIR/dist/." "$OUTPUT_DIR/frontend/"
cp "$JAR_PATH" "$OUTPUT_DIR/backend/app.jar"

cat > "$OUTPUT_DIR/release-metadata.json" <<EOF
{
  "releaseId": "$RELEASE_ID",
  "apiBaseUrl": "$API_BASE_URL",
  "jarName": "$(basename "$JAR_PATH")",
  "builtAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF

tar -C "$OUTPUT_DIR" -czf "$ARCHIVE_PATH" .

printf '\nRelease directory: %s\n' "$OUTPUT_DIR"
printf 'Release archive: %s\n' "$ARCHIVE_PATH"
