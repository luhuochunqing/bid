#!/usr/bin/env bash
# Input: staged Java files from git index and repository root state
# Output: pre-commit validation result for Java coding standards and compile gate
# Pos: scripts/质量守卫脚本
# 维护声明: 仅维护 Java 提交门禁；规则变更请同步团队编码规范与 hooks 文档。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

DEFAULT_SERVICE_TARGET_PREFIXES=(
  "backend/src/main/java/com/xiyu/bid/settings/service/"
  "backend/src/main/java/com/xiyu/bid/projectworkflow/service/"
  "backend/src/main/java/com/xiyu/bid/analytics/service/"
)

parse_prefixes() {
  local raw_value="$1"

  if [ -z "$raw_value" ]; then
    if [ "${#DEFAULT_SERVICE_TARGET_PREFIXES[@]}" -eq 0 ]; then
      return
    fi

    printf "%s\n" "${DEFAULT_SERVICE_TARGET_PREFIXES[@]}"
    return
  fi

  local prefix
  while IFS= read -r prefix; do
    if [ -n "$prefix" ]; then
      printf "%s\n" "$prefix"
    fi
  done <<EOF
$(printf "%s" "$raw_value" | tr ',' '\n')
EOF
}

build_quality_includes() {
  local file
  local relative_source
  local includes=()

  for file in "$@"; do
    relative_source="${file#backend/src/main/java/}"
    if [ "$relative_source" = "$file" ]; then
      continue
    fi

    includes+=("$relative_source")
  done

  if [ "${#includes[@]}" -eq 0 ]; then
    printf ""
    return
  fi

  local joined
  joined="$(IFS=,; echo "${includes[*]}")"
  printf "%s" "$joined"
}

build_quality_only_analyze() {
  local file
  local relative_source
  local classes=()

  for file in "$@"; do
    relative_source="${file#backend/src/main/java/}"
    if [ "$relative_source" = "$file" ]; then
      continue
    fi

    classes+=("${relative_source%.java}")
  done

  if [ "${#classes[@]}" -eq 0 ]; then
    printf ""
    return
  fi

  local i
  for i in "${!classes[@]}"; do
    classes[$i]="${classes[$i]//\//.}"
  done

  local joined
  joined="$(IFS=,; echo "${classes[*]}")"
  printf "%s" "$joined"
}

filter_target_files() {
  local prefixes_csv="$1"
  shift

  local prefixes=()
  local prefixes_count=0
  local prefix
  while IFS= read -r prefix; do
    if [ -n "$prefix" ]; then
      prefixes+=("$prefix")
      prefixes_count=$((prefixes_count + 1))
    fi
  done <<EOF
$prefixes_csv
EOF

  if [ "$prefixes_count" -eq 0 ]; then
    return
  fi

  local file
  for file in "$@"; do
    for prefix in "${prefixes[@]}"; do
      if [[ "$file" == "$prefix"* ]]; then
        printf "%s\n" "$file"
        break
      fi
    done
  done
}

STAGED_JAVA_FILES=()
while IFS= read -r file; do
  if [ -n "$file" ]; then
    STAGED_JAVA_FILES+=("$file")
  fi
done <<EOF
$(git diff --cached --name-only --diff-filter=ACMR | grep -E '^backend/src/main/java/.*\.java$' || true)
EOF

if [ "${#STAGED_JAVA_FILES[@]}" -eq 0 ]; then
  echo "java-standards: no staged Java files, skip."
  exit 0
fi

QUALITY_INCLUDES="$(build_quality_includes "${STAGED_JAVA_FILES[@]}")"
QUALITY_ONLY_ANALYZE="$(build_quality_only_analyze "${STAGED_JAVA_FILES[@]}")"

echo "java-standards: checking ${#STAGED_JAVA_FILES[@]} staged Java file(s)..."

HAS_ERROR=0

for file in "${STAGED_JAVA_FILES[@]}"; do
  staged_patch="$(git diff --cached -U0 -- "$file" | grep -E '^\+' | grep -vE '^\+\+\+' || true)"

  if [ -z "$staged_patch" ]; then
    continue
  fi

  if printf "%s\n" "$staged_patch" | grep -nE 'catch[[:space:]]*\([[:space:]]*Exception[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*\)' >/tmp/java_hook_match.txt; then
    echo "ERROR: avoid broad catch(Exception) in $file"
    sed 's/^/  line /' /tmp/java_hook_match.txt
    HAS_ERROR=1
  fi

  if printf "%s\n" "$staged_patch" | grep -nE 'Optional[^;]*\.get[[:space:]]*\(' >/tmp/java_hook_match.txt; then
    echo "ERROR: avoid Optional.get() in $file; use map/flatMap/orElseThrow."
    sed 's/^/  line /' /tmp/java_hook_match.txt
    HAS_ERROR=1
  fi

  if printf "%s\n" "$staged_patch" | grep -nE 'throw[[:space:]]+new[[:space:]]+IllegalArgumentException[[:space:]]*\(' >/tmp/java_hook_match.txt; then
    echo "WARN: consider domain-specific exception instead of IllegalArgumentException in $file"
    sed 's/^/  line /' /tmp/java_hook_match.txt
  fi

  if printf "%s\n" "$staged_patch" | grep -nE 'import[[:space:]]+.+\.\*[[:space:]]*;' >/tmp/java_hook_match.txt; then
    echo "ERROR: avoid wildcard import in $file"
    sed 's/^/  line /' /tmp/java_hook_match.txt
    HAS_ERROR=1
  fi

  if printf "%s\n" "$staged_patch" | grep -nE 'System\.(out|err)\.print' >/tmp/java_hook_match.txt; then
    echo "ERROR: avoid System.out/err print in $file; use logger."
    sed 's/^/  line /' /tmp/java_hook_match.txt
    HAS_ERROR=1
  fi

  if printf "%s\n" "$staged_patch" | grep -nE '\b(List|Set|Map|Optional)\s+[A-Za-z_][A-Za-z0-9_]*\s*[=;,\)]' >/tmp/java_hook_match.txt; then
    echo "ERROR: avoid raw generic types in $file"
    sed 's/^/  line /' /tmp/java_hook_match.txt
    HAS_ERROR=1
  fi
done

if [ "$HAS_ERROR" -ne 0 ]; then
  echo
  echo "java-standards: blocked by rule violations."
  exit 1
fi

PMD_TARGET_PREFIXES="$(parse_prefixes "${JAVA_STANDARDS_PMD_TARGET_PREFIXES:-}")"
SPOTBUGS_TARGET_PREFIXES="$(parse_prefixes "${JAVA_STANDARDS_SPOTBUGS_TARGET_PREFIXES:-}")"

PMD_TARGET_FILES=()
SPOTBUGS_TARGET_FILES=()
PMD_TARGET_COUNT=0
SPOTBUGS_TARGET_COUNT=0
while IFS= read -r file; do
  if [ -n "$file" ]; then
    PMD_TARGET_FILES+=("$file")
    PMD_TARGET_COUNT=$((PMD_TARGET_COUNT + 1))
  fi
done <<EOF
$(filter_target_files "$PMD_TARGET_PREFIXES" "${STAGED_JAVA_FILES[@]}")
EOF
while IFS= read -r file; do
  if [ -n "$file" ]; then
    SPOTBUGS_TARGET_FILES+=("$file")
    SPOTBUGS_TARGET_COUNT=$((SPOTBUGS_TARGET_COUNT + 1))
  fi
done <<EOF
$(filter_target_files "$SPOTBUGS_TARGET_PREFIXES" "${STAGED_JAVA_FILES[@]}")
EOF

if [ "$PMD_TARGET_COUNT" -gt 0 ]; then
  PMD_TARGET_INCLUDES="$(build_quality_includes "${PMD_TARGET_FILES[@]}")"
  PMD_TARGET_ONLY_ANALYZE="$(build_quality_only_analyze "${PMD_TARGET_FILES[@]}")"
else
  PMD_TARGET_INCLUDES=""
  PMD_TARGET_ONLY_ANALYZE=""
fi

if [ "$SPOTBUGS_TARGET_COUNT" -gt 0 ]; then
  SPOTBUGS_TARGET_INCLUDES="$(build_quality_includes "${SPOTBUGS_TARGET_FILES[@]}")"
  SPOTBUGS_TARGET_ONLY_ANALYZE="$(build_quality_only_analyze "${SPOTBUGS_TARGET_FILES[@]}")"
else
  SPOTBUGS_TARGET_INCLUDES=""
  SPOTBUGS_TARGET_ONLY_ANALYZE=""
fi

SPOTBUGS_MODE="${JAVA_STANDARDS_SPOTBUGS:-auto}"
ENABLE_SPOTBUGS=0
PMD_MODE="${JAVA_STANDARDS_PMD:-off}"
ENABLE_PMD=0

if [ "$SPOTBUGS_MODE" = "on" ]; then
  ENABLE_SPOTBUGS=1
elif [ "$SPOTBUGS_MODE" = "report" ]; then
  ENABLE_SPOTBUGS=1
elif [ "$SPOTBUGS_MODE" = "off" ]; then
  ENABLE_SPOTBUGS=0
elif [ "$SPOTBUGS_MODE" = "auto" ]; then
  if command -v curl >/dev/null 2>&1 && curl -fsSIL --connect-timeout 3 --max-time 8 "https://repo.maven.apache.org/maven2/" >/dev/null 2>&1; then
    ENABLE_SPOTBUGS=1
  fi
else
  echo "ERROR: invalid JAVA_STANDARDS_SPOTBUGS value: $SPOTBUGS_MODE (use auto|report|on|off)"
  exit 1
fi

if [ "$PMD_MODE" = "on" ]; then
  ENABLE_PMD=1
elif [ "$PMD_MODE" = "report" ]; then
  ENABLE_PMD=1
elif [ "$PMD_MODE" = "off" ]; then
  ENABLE_PMD=0
elif [ "$PMD_MODE" = "auto" ]; then
  ENABLE_PMD=0
else
  echo "ERROR: invalid JAVA_STANDARDS_PMD value: $PMD_MODE (use auto|report|on|off)"
  exit 1
fi

run_checkstyle_gate() {
  (cd backend && mvn -q \
    -Dmaven.test.skip=true \
    -Djacoco.skip=true \
    -P"java-quality" \
    -Dquality.skip=false \
    -Dquality.includes="$QUALITY_INCLUDES" \
    -Dquality.onlyAnalyze="$QUALITY_ONLY_ANALYZE" \
    checkstyle:check)
}

run_pmd_gate() {
  (cd backend && mvn -q \
    -Dmaven.test.skip=true \
    -Djacoco.skip=true \
    -P"java-quality" \
    -Dquality.skip=false \
    -Dquality.includes="$PMD_TARGET_INCLUDES" \
    -Dquality.onlyAnalyze="$PMD_TARGET_ONLY_ANALYZE" \
    pmd:check)
}

run_pmd_report() {
  (cd backend && mvn -q \
    -Dmaven.test.skip=true \
    -Djacoco.skip=true \
    -P"java-quality" \
    -Dquality.skip=false \
    -Dquality.includes="$PMD_TARGET_INCLUDES" \
    -Dquality.onlyAnalyze="$PMD_TARGET_ONLY_ANALYZE" \
    pmd:pmd)
}

run_spotbugs_gate() {
  (cd backend && mvn -q \
    -Dmaven.test.skip=true \
    -Djacoco.skip=true \
    -P"java-quality,java-quality-spotbugs" \
    -Dquality.skip=false \
    -Dquality.includes="$SPOTBUGS_TARGET_INCLUDES" \
    -Dquality.onlyAnalyze="$SPOTBUGS_TARGET_ONLY_ANALYZE" \
    spotbugs:check)
}

run_spotbugs_report() {
  (cd backend && mvn -q \
    -Dmaven.test.skip=true \
    -Djacoco.skip=true \
    -P"java-quality,java-quality-spotbugs" \
    -Dquality.skip=false \
    -Dquality.includes="$SPOTBUGS_TARGET_INCLUDES" \
    -Dquality.onlyAnalyze="$SPOTBUGS_TARGET_ONLY_ANALYZE" \
    spotbugs:spotbugs)
}

echo "java-standards: running checkstyle gate..."
run_checkstyle_gate

if [ "$ENABLE_PMD" -eq 1 ] && [ "$PMD_TARGET_COUNT" -gt 0 ]; then
  echo "java-standards: PMD target scope: $PMD_TARGET_INCLUDES"
fi

if [ "$ENABLE_PMD" -eq 1 ]; then
  if [ "$PMD_TARGET_COUNT" -eq 0 ]; then
    echo "java-standards: PMD enabled (mode=$PMD_MODE) but no staged files in target service packages, skip PMD."
  elif [ "$PMD_MODE" = "report" ]; then
    echo "java-standards: PMD report mode on $PMD_TARGET_COUNT target file(s)."
    run_pmd_report
  else
    echo "java-standards: PMD gate on $PMD_TARGET_COUNT target file(s)."
    run_pmd_gate
  fi
else
  echo "java-standards: PMD disabled (mode=$PMD_MODE)."
fi

if [ "$ENABLE_SPOTBUGS" -eq 1 ] && [ "$SPOTBUGS_MODE" = "auto" ]; then
  echo "java-standards: spotbugs auto mode scoped to target service packages."
fi

if [ "$ENABLE_SPOTBUGS" -eq 1 ] && [ "$SPOTBUGS_TARGET_COUNT" -gt 0 ]; then
  echo "java-standards: spotbugs target scope: $SPOTBUGS_TARGET_INCLUDES"
fi

if [ "$ENABLE_SPOTBUGS" -eq 1 ]; then
  if [ "$SPOTBUGS_TARGET_COUNT" -eq 0 ]; then
    echo "java-standards: spotbugs enabled (mode=$SPOTBUGS_MODE) but no staged files in target service packages, skip spotbugs."
  elif [ "$SPOTBUGS_MODE" = "report" ]; then
    echo "java-standards: spotbugs report mode on $SPOTBUGS_TARGET_COUNT target file(s)."
    run_spotbugs_report
  else
    echo "java-standards: running spotbugs gate on $SPOTBUGS_TARGET_COUNT target file(s)..."
    if ! run_spotbugs_gate; then
      if [ "$SPOTBUGS_MODE" = "auto" ]; then
        echo "java-standards: spotbugs failed in auto mode, skip spotbugs."
      else
        exit 1
      fi
    fi
  fi
else
  echo "java-standards: spotbugs disabled (mode=$SPOTBUGS_MODE)."
fi

if [ "$ENABLE_PMD" -eq 1 ] && [ "$PMD_MODE" = "report" ]; then
  echo "java-standards: PMD report mode is non-blocking by design."
fi

if [ "$ENABLE_SPOTBUGS" -eq 1 ] && [ "$SPOTBUGS_MODE" = "report" ]; then
  echo "java-standards: spotbugs report mode is non-blocking by design."
fi

echo "java-standards: passed."
