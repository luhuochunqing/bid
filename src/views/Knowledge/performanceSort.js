// CO-583 业绩列表分组排序纯函数
// 需求：前端按 groupCompany 拼音 ASC → signingDate ASC → expiryDate ASC 排序，空 groupCompany 排最后
// Pos: src/views/Knowledge/ - Performance list sort helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
// 维护声明: 排序规则需与后端 PerformanceExcelExporter.writeExportSheet 的 TreeMap+Collator 排序保持一致。
// 当前假设数据量 < 1000 条客户端分页；若改为服务端分页，需迁移排序逻辑到后端 Specification。

/**
 * 业绩列表客户端排序：集团拼音 ASC → 签约日期 ASC → 截止日期 ASC，空集团排最后。
 * 不修改原数组。
 * @param {Array} records - 业绩记录列表
 * @returns {Array} 排序后的新数组
 */
export function sortPerformanceByGroupPinyin(records) {
  if (!Array.isArray(records) || records.length === 0) return []
  return [...records].sort((a, b) => compareByGroupPinyin(a, b))
}

function compareByGroupPinyin(a, b) {
  const ga = a?.groupCompany
  const gb = b?.groupCompany
  const aEmpty = !ga || String(ga).trim() === ''
  const bEmpty = !gb || String(gb).trim() === ''
  if (aEmpty && bEmpty) return 0
  if (aEmpty) return 1
  if (bEmpty) return -1
  const cmp = String(ga).localeCompare(String(gb), 'zh-CN')
  if (cmp !== 0) return cmp
  const s = compareDate(a?.signingDate, b?.signingDate)
  if (s !== 0) return s
  return compareDate(a?.expiryDate, b?.expiryDate)
}

function compareDate(a, b) {
  if (!a && !b) return 0
  if (!a) return 1
  if (!b) return -1
  return a < b ? -1 : a > b ? 1 : 0
}
