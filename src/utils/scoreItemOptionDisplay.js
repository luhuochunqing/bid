// Input: score item { code, dim, detail } from AI 评分标准解析
// Output: short select label, original-text tooltip, and query match
// Pos: src/utils - shared display helpers for score-item dropdowns
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

function text(value) {
  return String(value || '').trim()
}

export function scoreItemOptionTitle(item) {
  if (!item) return ''
  return [text(item.code), text(item.dim)].filter(Boolean).join(' ')
}

export function scoreItemOptionTooltip(item) {
  if (!item) return ''
  return text(item.detail)
}

export function scoreItemMatchesQuery(item, query) {
  const needle = text(query).toLowerCase()
  if (!needle) return true
  if (!item) return false
  const hay = [item.code, item.dim, item.detail]
    .map((value) => text(value).toLowerCase())
    .join('\n')
  return hay.includes(needle)
}

