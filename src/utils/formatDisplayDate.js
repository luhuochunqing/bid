// Input: raw backend datetime string (ISO 8601 with T separator) or Date / null
// Output: display-friendly string "yyyy-MM-dd HH:mm:ss" / "yyyy-MM-dd" with fallback
// Pos: src/utils/ - Display helper for CO-472 (T separator fix)
//
// 设计说明：
// - 字符串切片优先，避免 new Date() 触发 UTC 时区偏移导致日期错位
// - 仅日期 / 仅时间 / Date 对象走分支处理
// - 非日期字符串返回 fallback，不原样透传（避免把脏数据展示给用户）

const DATETIME_RE = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}/
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/

function pad(n) {
  return String(n).padStart(2, '0')
}

function fromDate(d, withTime) {
  const datePart = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  return withTime
    ? `${datePart} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    : datePart
}

/**
 * 显示用日期时间格式化：把 ISO 8601 "2026-07-05T11:33:30" 转为 "2026-07-05 11:33:30"。
 * @param {string|number|Date|null} value
 * @param {string} fallback 空值或非法值时返回的占位符，默认 '-'
 * @returns {string}
 */
export function formatDisplayDateTime(value, fallback = '-') {
  if (value === null || value === undefined || value === '') return fallback
  if (value instanceof Date) {
    return isNaN(value.getTime()) ? fallback : fromDate(value, true)
  }
  const str = String(value)
  if (DATETIME_RE.test(str)) {
    return str.slice(0, 19).replace('T', ' ')
  }
  if (DATE_RE.test(str)) {
    return str.slice(0, 10)
  }
  const d = new Date(str)
  return isNaN(d.getTime()) ? fallback : fromDate(d, true)
}

/**
 * 显示用日期格式化：从 ISO 8601 或日期字符串提取 "yyyy-MM-dd"。
 * @param {string|number|Date|null} value
 * @param {string} fallback 空值或非法值时返回的占位符，默认 '-'
 * @returns {string}
 */
export function formatDisplayDate(value, fallback = '-') {
  if (value === null || value === undefined || value === '') return fallback
  if (value instanceof Date) {
    return isNaN(value.getTime()) ? fallback : fromDate(value, false)
  }
  const str = String(value)
  if (/^\d{4}-\d{2}-\d{2}/.test(str)) {
    return str.slice(0, 10)
  }
  const d = new Date(str)
  return isNaN(d.getTime()) ? fallback : fromDate(d, false)
}
