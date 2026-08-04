/**
 * 文件大小格式化工具（D3-5 修复：消除 PerformanceBundleExportDialog 与 WarehouseExportPackageDetail 的重复实现）。
 *
 * <p>统一规则：
 * <ul>
 *   <li>B：整数</li>
 *   <li>KB/MB/GB：保留 2 位小数（与 WarehouseExportPackageDetail 一致）</li>
 *   <li>自动选择最合适的单位（1024 进制）</li>
 *   <li>0 或负数返回 fallback 字符（默认 '—'）</li>
 * </ul>
 */

const UNITS = ['B', 'KB', 'MB', 'GB']
const FALLBACK = '—'

/**
 * 将字节数格式化为带单位的可读字符串。
 *
 * @param {number|null|undefined} bytes 字节数
 * @param {string} [fallback='—'] 无效值时的返回字符串
 * @returns {string} 格式化后的字符串，如 "1.50 MB"
 */
export function formatBytes(bytes, fallback = FALLBACK) {
  if (bytes == null || bytes <= 0 || !Number.isFinite(bytes)) return fallback
  let v = bytes
  let i = 0
  while (v >= 1024 && i < UNITS.length - 1) {
    v /= 1024
    i++
  }
  const decimals = i > 0 ? 2 : 0
  return `${v.toFixed(decimals)} ${UNITS[i]}`
}

export default formatBytes
