#!/usr/bin/env bash
# Input: XIYU_EVAL_CORPUS_DIR 指向的真实 docx/pdf 语料目录（nightly 全链路采集用）
# Output: 仅 echo 规划的 curl 调用模板（TODO 状态，不真调 LLM）；缺语料目录时明确 SKIP 以 0 退出
# Pos: scripts/eval-scoreparse —— spec 057 US3 第二层模板；与 GITEE 无关，指向测试环境解析 API
set -euo pipefail

# ── 语料目录门禁：未设置 → 明确 SKIP 原因并成功退出（CI 不挂）──
if [[ -z "${XIYU_EVAL_CORPUS_DIR:-}" ]]; then
  echo "[eval-scoreparse:nightly] SKIP: 未设置 XIYU_EVAL_CORPUS_DIR。"
  echo "  本脚本是第二层全链路采集模板：需要真实 docx/pdf 语料目录才会规划采集任务。"
  echo "  用法: XIYU_EVAL_CORPUS_DIR=/path/to/corpus bash scripts/eval-scoreparse/nightly-full.sh"
  exit 0
fi

CORPUS_DIR="${XIYU_EVAL_CORPUS_DIR}"
if [[ ! -d "${CORPUS_DIR}" ]]; then
  echo "[eval-scoreparse:nightly] SKIP: XIYU_EVAL_CORPUS_DIR 不是目录: ${CORPUS_DIR}"
  exit 0
fi

BASE_URL="${XIYU_PARSE_BASE_URL:-http://localhost:18089}"
PROJECT_ID="${XIYU_EVAL_PROJECT_ID:-<projectId>}"

echo "[eval-scoreparse:nightly] 规划全链路采集: corpus=${CORPUS_DIR} api=${BASE_URL}"
echo "[eval-scoreparse:nightly] TODO(测试环境密钥注入): 以下 curl 均为规划模板，需在测试环境注入鉴权与密钥后启用，当前不执行任何请求、不消耗 LLM 配额。"

shopt -s nullglob nocaseglob
count=0
for file in "${CORPUS_DIR}"/*.docx "${CORPUS_DIR}"/*.pdf; do
  count=$((count + 1))
  cat <<TEMPLATE
[PLAN ${count}] ${file}
  # 1) 触发解析（score-parse API，走 sidecar + LLM 全链路）:
  curl -sS -X POST "${BASE_URL}/api/projects/${PROJECT_ID}/score-parse/parse" \\
    -H "Authorization: Bearer \${XIYU_EVAL_API_TOKEN}" \\
    -H "Content-Type: application/json" \\
    -d '{"fileName":"$(basename "${file}")","fileUrl":"<对象存储地址,先经文档上传接口获取>"}'
  # 2) 轮询进度直至 COMPLETED:
  curl -sS "${BASE_URL}/api/projects/${PROJECT_ID}/score-parse/parse/status" \\
    -H "Authorization: Bearer \${XIYU_EVAL_API_TOKEN}"
  # 3) 拉取解析结果清单并与金标比对（对齐率/守卫计数落 nightly 存档）:
  curl -sS "${BASE_URL}/api/projects/${PROJECT_ID}/score-parse/items" \\
    -H "Authorization: Bearer \${XIYU_EVAL_API_TOKEN}"

TEMPLATE
done
shopt -u nocaseglob

if [[ ${count} -eq 0 ]]; then
  echo "[eval-scoreparse:nightly] SKIP: 目录下没有 *.docx / *.pdf 文件: ${CORPUS_DIR}"
  exit 0
fi

echo "[eval-scoreparse:nightly] 共规划 ${count} 个语料的采集模板（未执行）。"
