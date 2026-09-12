#!/usr/bin/env bash
# Input: local real-corpus markdown under XIYU_EVAL_CORPUS_DIR (never committed)
# Output: candidates.json snapshot consumed by Incident1358RealCorpusGoldenTest
# Pos: scripts/eval-scoreparse — explicit real-corpus recorder entrypoint
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

if [[ -z "${XIYU_EVAL_CORPUS_DIR:-}" ]]; then
  echo "[scoreparse-recorder] ERROR: 未设置 XIYU_EVAL_CORPUS_DIR。"
  echo "  示例：XIYU_EVAL_CORPUS_DIR=/secure/local/scoreparse-corpus npm run eval:scoreparse:record-real-corpus"
  exit 2
fi

CASE_ID="${XIYU_SCOREPARSE_REAL_CASE:-incident-1358}"
MARKDOWN_PATH="${XIYU_SCOREPARSE_MARKDOWN:-${XIYU_EVAL_CORPUS_DIR}/${CASE_ID}/source.md}"
OUTPUT_PATH="${XIYU_SCOREPARSE_CANDIDATES:-${XIYU_EVAL_CORPUS_DIR}/${CASE_ID}/candidates.json}"

if [[ ! -f "${MARKDOWN_PATH}" ]]; then
  echo "[scoreparse-recorder] ERROR: 未找到输入 markdown: ${MARKDOWN_PATH}"
  echo "  先把真实文档经 sidecar/文档提取链导出的 markdown 放到该路径，或设置 XIYU_SCOREPARSE_MARKDOWN。"
  exit 2
fi

case "${MARKDOWN_PATH}" in
  "${ROOT_DIR}"/*)
    echo "[scoreparse-recorder] ERROR: 输入 markdown 位于仓库目录内，疑似真实客户语料入仓风险: ${MARKDOWN_PATH}"
    echo "  请把真实语料放在仓库外的 XIYU_EVAL_CORPUS_DIR。"
    exit 2
    ;;
esac

export XIYU_RECORD_SCOREPARSE_CORPUS=true
export XIYU_SCOREPARSE_REAL_CASE="${CASE_ID}"
export XIYU_SCOREPARSE_MARKDOWN="${MARKDOWN_PATH}"
export XIYU_SCOREPARSE_CANDIDATES="${OUTPUT_PATH}"

echo "[scoreparse-recorder] recording case=${CASE_ID}"
echo "[scoreparse-recorder] input=${MARKDOWN_PATH}"
echo "[scoreparse-recorder] output=${OUTPUT_PATH}"

mvn -f "${ROOT_DIR}/backend/pom.xml" \
  -Dtest=ScoreParseRealCorpusRecorderTest \
  -DfailIfNoTests=false \
  test

echo "[scoreparse-recorder] done: ${OUTPUT_PATH}"
