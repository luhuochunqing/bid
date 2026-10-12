#!/usr/bin/env bash
# Input: committed portable source fixtures, local Java/Maven and Sidecar Python dependencies.
# Output: executable source/provenance/identity/full-rule/full-pipeline/empty-rule adoption regressions (含 1382 Task291 线上事实全链路重放); any missing dependency or failure is fatal.
# Pos: scripts scoreparse root-cause pre-push gate (offline; no private corpus or services).
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export SIDECAR_PYTHON="${SIDECAR_PYTHON:-python3}"
if ! "$SIDECAR_PYTHON" -B -c 'import docx, lxml' >/dev/null 2>&1; then
  if [ -x "$ROOT_DIR/document-converter-sidecar/.venv/bin/python" ]; then
    export SIDECAR_PYTHON="$ROOT_DIR/document-converter-sidecar/.venv/bin/python"
  fi
fi
"$SIDECAR_PYTHON" -B -c 'import docx, lxml'
cd "$ROOT_DIR"
mvn -o -q -f backend/pom.xml '-Dtest=RealSourceExcerptReplayTest,CrossFormatDocumentSourceReplayTest,FullRuleSourceReplayTest,Project1382FullPipelineIntegrationTest,TextPrimaryScoreFactMapperTest,MultimodalScoreFactValidatorTest,TextPrimaryEvidenceBinderTest,ScoreReviewAdoptionPolicyTest,ScoreExtractionSnapshotTest,SchemeScopePublicationTest' test
