#!/usr/bin/env bash
# Input: committed portable source fixtures, local Java/Maven and Sidecar Python dependencies.
# Output: executable source/provenance/identity/full-rule regressions; any missing dependency or failure is fatal.
# Pos: scripts scoreparse root-cause pre-push gate (offline; no private corpus or services).
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export SIDECAR_PYTHON="${SIDECAR_PYTHON:-python3}"
"$SIDECAR_PYTHON" -B -c 'import docx, lxml'
cd "$ROOT_DIR"
mvn -o -q -f backend/pom.xml '-Dtest=RealSourceExcerptReplayTest,CrossFormatDocumentSourceReplayTest,FullRuleSourceReplayTest,TextPrimaryScoreFactMapperTest,MultimodalScoreFactValidatorTest,TextPrimaryEvidenceBinderTest,ScoreExtractionSnapshotTest,SchemeScopePublicationTest' test
