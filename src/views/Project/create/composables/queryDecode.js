// Input: route.query 原始值（string | array | undefined | null）
// Output: decodeQueryValue / decodeNumericQuery / splitTags 纯函数
// Pos: src/views/Project/create/composables/ - 路由 query 解码工具（自 useProjectCreateModel.js 拆出，line-budget 300 约束）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export const decodeQueryValue = (value) => {
  if (Array.isArray(value)) return decodeQueryValue(value[0])
  if (value === undefined || value === null) return ''
  return String(value)
}

export const decodeNumericQuery = (value) => {
  const normalized = decodeQueryValue(value)
  if (!normalized) return null
  const numericValue = Number(normalized)
  return Number.isFinite(numericValue) ? numericValue : null
}

export const splitTags = (value) => {
  const normalized = decodeQueryValue(value)
  if (!normalized) return []
  return normalized.split(',').map((item) => item.trim()).filter(Boolean)
}
