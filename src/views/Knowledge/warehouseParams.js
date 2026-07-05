/**
 * 仓库列表查询参数构造器（纯函数）。
 *
 * 提取自 Warehouse.vue 的 buildParams，目的是让筛选参数构造逻辑可单测。
 * 后端 WarehouseController 用 @RequestParam String types/statuses/regions/provinces
 * 接收（CSV 逗号分隔单参数，见 WarehouseController#parseCsv / parseEnums），
 * 因此前端必须把多选数组 join 成 CSV 字符串再传，否则 axios 默认会序列化为
 * types[]=A&types[]=B，Spring String 只取第一个值，多选筛选失效。
 */

/**
 * 默认排除已关仓（CLOSED）。仅当用户显式选中 CLOSED 时才把 CLOSED 纳入查询。
 * 用户未选任何状态时，默认查 IN_USE/EXPIRING/EXPIRED 三个非关仓状态。
 */
const DEFAULT_OPEN_STATUSES = ['IN_USE', 'EXPIRING', 'EXPIRED']

/**
 * 根据筛选条件构造后端 list 接口的查询参数对象。
 *
 * @param {object} filters WarehouseFilterBar 输出的筛选条件
 * @param {number} page 0-based 页码
 * @param {number} size 每页条数
 * @returns {object} 可直接传给 axios 的 params 对象
 */
export function buildWarehouseListParams(filters, page, size) {
  const f = filters || {}
  const p = { page, size }

  if (f.keyword) p.keyword = f.keyword

  // 多选数组 → CSV 字符串，与后端 @RequestParam String + parseCsv/parseEnums 对齐
  if (f.types?.length) p.types = f.types.join(',')
  if (f.regions?.length) p.regions = f.regions.join(',')
  if (f.provinces?.length) p.provinces = f.provinces.join(',')

  // statuses：用户选了就尊重用户选择（含或不含 CLOSED 都原样传）；
  // 用户没选时默认排除 CLOSED，传 IN_USE/EXPIRING/EXPIRED。
  if (f.statuses?.length) {
    p.statuses = f.statuses.join(',')
  } else {
    p.statuses = DEFAULT_OPEN_STATUSES.join(',')
  }

  if (f.endDateFrom) p.endDateFrom = f.endDateFrom
  if (f.endDateTo) p.endDateTo = f.endDateTo
  if (f.hasPropertyCert) p.hasPropertyCert = true
  if (f.hasInvoice) p.hasInvoice = true
  if (f.hasPhotos) p.hasPhotos = true
  if (f.hasLeaseContract) p.hasLeaseContract = true
  if (f.contactPersonKeyword) p.contactPersonKeyword = f.contactPersonKeyword

  return p
}
