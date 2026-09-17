// Input: 筛选条件、X 轴维度、全局日期范围、部门树选项、人员选项
// Output: 趋势分析 API 参数对象及部门/人员参数正规化方法（防止全选时产生超长 URL 导致 HTTP 414）
// Pos: src/views/data-analysis/components/m1-trend-analysis/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 递归计算树形选项节点总数。
 * @param {Array} nodes
 * @returns {number}
 */
export function countTreeNodes(nodes) {
  if (!Array.isArray(nodes) || nodes.length === 0) return 0
  let count = 0
  for (const node of nodes) {
    count += 1
    if (Array.isArray(node.children) && node.children.length > 0) {
      count += countTreeNodes(node.children)
    }
  }
  return count
}

/**
 * 递归收集树形选项中所有非空的唯一 value 集合。
 * 生产真实部门树存在跨分支重名部门（如华东大区/综合部 与 华北大区/综合部），
 * 全选收集去重后的数组长度小于总节点数，判定全选必须以唯一 value 集合为基准。
 * @param {Array} nodes
 * @returns {Set}
 */
export function getUniqueTreeValues(nodes) {
  const set = new Set()
  function walk(list) {
    for (const node of list || []) {
      if (node && node.value != null && node.value !== '') {
        set.add(node.value)
      }
      if (Array.isArray(node?.children) && node.children.length > 0) {
        walk(node.children)
      }
    }
  }
  walk(nodes)
  return set
}

/**
 * 判断当前选中的部门是否代表全选（全部）。
 * 必须基于去重后的唯一部门名称集合大小判定，防止同名节点导致的数量偏差。
 * @param {Array} selected
 * @param {Array} departmentOptions
 * @returns {boolean}
 */
export function isAllDepartmentsSelected(selected, departmentOptions) {
  if (!Array.isArray(selected) || selected.length === 0) return false
  const uniqueVals = getUniqueTreeValues(departmentOptions)
  if (uniqueVals.size === 0) return false
  return selected.length >= uniqueVals.size
}

/**
 * 正规化部门筛选列表：
 * 若已全选（包含整棵部门树），返回空数组，向后端表达全量查询（不带 query 参数），
 * 彻底防止 URL Query 拼接几百个部门导致 HTTP 414 URI Too Long。
 *
 * @param {Array} departments
 * @param {Array} departmentOptions
 * @returns {Array}
 */
export function normalizeDepartmentFilter(departments, departmentOptions) {
  if (isAllDepartmentsSelected(departments, departmentOptions)) {
    return []
  }
  return Array.isArray(departments) ? departments : []
}

/**
 * 判断当前选中的人员是否代表全选。
 * @param {Array} selected
 * @param {Array} personOptions
 * @returns {boolean}
 */
export function isAllPersonsSelected(selected, personOptions) {
  if (!Array.isArray(selected) || selected.length === 0) return false
  const total = Array.isArray(personOptions) ? personOptions.length : 0
  if (total === 0) return false
  return selected.length >= total
}

/**
 * 正规化人员筛选列表：
 * 若已全选，返回空数组，向后端表达全量查询，防止拼接 8000+ 个人员引发 HTTP 414。
 * @param {Array} persons
 * @param {Array} personOptions
 * @returns {Array}
 */
export function normalizePersonFilter(persons, personOptions) {
  if (isAllPersonsSelected(persons, personOptions)) {
    return []
  }
  return Array.isArray(persons) ? persons : []
}

/**
 * 格式化日期为 YYYY-MM-DD。
 * @param {Date|string} date
 * @returns {string|null}
 */
export function formatDateStr(date) {
  if (!date) return null
  if (typeof date === 'string') return date.slice(0, 10)
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return null
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * 筛选字段 → 趋势分析增强 API 参数映射（PRD §6.7）。
 * @param {Object} filters
 * @param {string} xAxis
 * @param {Array} dateRange
 * @param {Array} [departmentOptions=[]]
 * @param {Array} [personOptions=[]]
 * @returns {Object}
 */
export function buildApiParams(filters, xAxis, dateRange, departmentOptions = [], personOptions = []) {
  const f = filters || {}
  const isTimeAxis = !xAxis || xAxis === 'time'
  const depts = normalizeDepartmentFilter(f.departments, departmentOptions)
  const persons = normalizePersonFilter(f.persons, personOptions)

  return {
    xAxis,
    startDate: formatDateStr(dateRange?.[0]),
    endDate: formatDateStr(dateRange?.[1]),
    // timeDimension 仅当 X 轴为时间维度时发送
    ...(isTimeAxis && f.timeDimension ? { timeDimension: f.timeDimension } : {}),
    ...(depts.length ? { departmentIds: depts.join(',') } : {}),
    ...(persons.length ? { userIds: persons.join(',') } : {}),
    ...(f.regions?.length ? { regionIds: f.regions.join(',') } : {}),
    ...(f.customerTypes?.length ? { customerTypes: f.customerTypes.join(',') } : {}),
    ...(f.projectTypes?.length ? { projectTypes: f.projectTypes.join(',') } : {}),
    ...(f.projectStatuses?.length ? { statuses: f.projectStatuses.join(',') } : {}),
    ...(f.tenderSubjects?.length ? { tenderEntities: f.tenderSubjects.join(',') } : {}),
    ...(f.competitors?.length ? { competitorNames: f.competitors.join(',') } : {})
  }
}
