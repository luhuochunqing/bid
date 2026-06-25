/**
 * Color utility functions for frontend display.
 */

/**
 * Convert a hex color string to an rgba string with 0.12 opacity.
 * Used for generating soft background colors from column/category colors.
 * @param {string|null|undefined} hex - Hex color (e.g. '#409eff', '409EFF', '#f56')
 * @returns {string} rgba string or fallback CSS variable
 */
export function hexToSoftBackground(hex) {
  if (typeof hex !== 'string' || !/^#?([\da-f]{3}|[\da-f]{6})$/i.test(hex)) {
    return 'var(--bg-subtle)'
  }
  let normalized = hex.replace('#', '')
  if (normalized.length === 3) {
    normalized = normalized.split('').map((c) => c + c).join('')
  }
  const r = parseInt(normalized.slice(0, 2), 16)
  const g = parseInt(normalized.slice(2, 4), 16)
  const b = parseInt(normalized.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, 0.12)`
}