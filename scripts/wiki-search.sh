#!/usr/bin/env bash
#=============================================================================
# wiki-search.sh — 西域投标平台项目文档轻量全文搜索
#
# 用法:
#   ./scripts/wiki-search.sh <关键词>
#   ./scripts/wiki-search.sh -i <关键词>   # 大小写不敏感
#   ./scripts/wiki-search.sh --list        # 列出可搜索目录
#
# 搜索范围: .wiki/pages/ + .wiki/extracts/ + docs/
# 基于 ripgrep (rg)，无额外依赖
#=============================================================================

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

SEARCH_DIRS=(
  "$ROOT/.wiki/pages"
  "$ROOT/.wiki/extracts"
  "$ROOT/docs"
)

CASE_INSENSITIVE=false
KEYWORD=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -i|--ignore-case)
      CASE_INSENSITIVE=true
      shift
      ;;
    --list)
      echo "可搜索目录："
      for d in "${SEARCH_DIRS[@]}"; do
        echo "  ${d#$ROOT/}"
      done
      exit 0
      ;;
    -h|--help)
      echo "用法: $0 [-i] <关键词>"
      echo "      $0 --list"
      exit 0
      ;;
    *)
      KEYWORD="$1"
      shift
      ;;
  esac
done

if [[ -z "$KEYWORD" ]]; then
  echo "错误：请指定搜索关键词"
  echo "用法: $0 [-i] <关键词>"
  exit 1
fi

found_any=false

if command -v rg &>/dev/null; then
  RG_OPTS=(-n --heading --color=always)
  $CASE_INSENSITIVE && RG_OPTS=(-n --heading --color=always -i)

  for dir in "${SEARCH_DIRS[@]}"; do
    if [[ -d "$dir" ]]; then
      set +e
      results=$(rg "${RG_OPTS[@]}" "$KEYWORD" "$dir" 2>/dev/null)
      set -e
      if [[ -n "$results" ]]; then
        found_any=true
        echo ""
        echo "=== ${dir#$ROOT/} ==="
        echo "$results"
      fi
    fi
  done
else
  for dir in "${SEARCH_DIRS[@]}"; do
    if [[ -d "$dir" ]]; then
      GREP_OPTS="-rn --color=always"
      $CASE_INSENSITIVE && GREP_OPTS="-rin --color=always"
      set +e
      results=$(grep $GREP_OPTS "$KEYWORD" "$dir" 2>/dev/null)
      set -e
      if [[ -n "$results" ]]; then
        found_any=true
        echo ""
        echo "=== ${dir#$ROOT/} ==="
        echo "$results"
      fi
    fi
  done
fi

if ! $found_any; then
  echo "未找到匹配 \"$KEYWORD\" 的文档"
fi
