export function normalizeCustomerPrediction(item = {}) {
  const evidenceRecords = Array.isArray(item?.evidenceRecords)
    ? item.evidenceRecords.map((record) => Number(record)).filter((record) => Number.isFinite(record))
    : []

  return {
    opportunityId: item?.opportunityId ?? item?.id ?? '',
    customerId: item?.customerId ?? item?.purchaserHash ?? '',
    suggestedProjectName: item?.suggestedProjectName || '待智能研判',
    predictedCategory: item?.predictedCategory || '---',
    predictedBudgetMin: Number(item?.predictedBudgetMin || 0),
    predictedBudgetMax: Number(item?.predictedBudgetMax || 0),
    predictedWindow: item?.predictedWindow || '待判断',
    confidence: Number(item?.confidence || 0),
    reasoningSummary: item?.reasoningSummary || '当前数据不足，暂无法生成高置信度预测。',
    evidenceRecords,
    convertedProjectId: item?.convertedProjectId || null,
  }
}

export function buildOpportunityProjectPayload(customer, currentUser, now = new Date()) {
  const customerName = customer?.customerName || '未命名客户'
  const prediction = customer?.prediction || {}
  const managerId = Number(currentUser?.id || 0)
  const tenderId = resolveOpportunityTenderId(prediction, customer)

  if (!managerId) {
    throw new Error('当前用户缺少可用 ID，无法创建项目')
  }
  if (!tenderId) {
    throw new Error('当前机会缺少来源标讯，无法直接转项目')
  }

  const averageBudget = Math.round(
    (Number(prediction.predictedBudgetMin || 0) + Number(prediction.predictedBudgetMax || 0)) / 2
  )
  const startDate = formatDateTime(now, '09:00:00')
  const endDate = buildDeadlineFromWindow(prediction.predictedWindow, now)

  return {
    name: prediction.suggestedProjectName || `${customerName}采购项目`,
    tenderId,
    managerId,
    teamMembers: [managerId],
    startDate,
    endDate,
    status: 'INITIATED',
    sourceModule: 'customer-opportunity-center',
    sourceCustomerId: customer?.customerId || '',
    sourceCustomer: customerName,
    sourceOpportunityId: String(prediction.opportunityId || ''),
    sourceReasoningSummary: prediction.reasoningSummary || '',
    customer: customerName,
    budget: averageBudget > 0 ? averageBudget : undefined,
    industry: customer?.industry || '',
    region: customer?.region || '',
    platform: customer?.predictedPlatform || '',
    description: prediction.reasoningSummary || '',
    remark: `预测时间窗口：${prediction.predictedWindow || '待判断'}；置信度：${normalizeConfidence(prediction.confidence)}%`,
    tagsJson: JSON.stringify(Array.isArray(customer?.mainCategories) ? customer.mainCategories : []),
  }
}

export function buildBoardSummaries({ customerInsights = [], customerPredictions = [], demoEnabled = true }) {
  if (!demoEnabled) {
    return [
      { label: '客户池', value: '--', note: '真实客户数据源未接入', tag: '未接入', tagType: 'info', placeholder: true, trendLabel: 'API' },
      { label: '采购记录', value: '--', note: '历史采购服务待真实数据源接入', tag: '未接入', tagType: 'info', placeholder: true, trendLabel: 'API' },
      { label: '预测商机', value: '--', note: '预测结果不会在真实模式下伪造', tag: '未接入', tagType: 'info', placeholder: true, trendLabel: 'API' },
      { label: '项目转化', value: '--', note: '转项目链路待真实数据源接入', tag: '未接入', tagType: 'info', placeholder: true, trendLabel: 'API' },
    ]
  }

  const highValueCount = customerInsights.filter((item) => Number(item.opportunityScore || 0) >= 85).length
  const shortTermCount = customerPredictions.filter((item) => isWindowWithinMonths(item.predictedWindow, [3, 4])).length
  const midTermCount = customerPredictions.filter((item) => isWindowWithinMonths(item.predictedWindow, [5, 6])).length
  const convertedCount = customerPredictions.filter((item) => Boolean(item.convertedProjectId)).length

  return [
    { label: '高价值客户', value: String(highValueCount), note: '核心经营资产', tag: '重点', tagType: 'success', trend: 12, isUp: true },
    { label: '30D 预测机会', value: String(shortTermCount), note: '需近期重点研判', tag: '紧迫', tagType: 'danger', trend: 8, isUp: true },
    { label: '远期潜客', value: String(midTermCount), note: '适合关系铺垫', tag: '观察', tagType: 'warning', trend: 3, isUp: false },
    { label: '已转化', value: String(convertedCount), note: '已转正式项目池', tag: '完成', tagType: 'info', trend: 20, isUp: true },
  ]
}

export function buildDrawerStats(history = []) {
  const totalCount = history.length
  const totalBudget = history.reduce((sum, item) => sum + Number(item?.budget || 0), 0)
  const categoryMap = new Map()

  history.forEach((item) => {
    const category = item?.category || '未知'
    categoryMap.set(category, (categoryMap.get(category) || 0) + 1)
  })

  const topCategory = [...categoryMap.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || '未知'
  return { totalCount, totalBudget, topCategory }
}

export function buildCategoryStats(history = []) {
  const total = history.length
  if (!total) {
    return []
  }

  const categoryMap = new Map()
  history.forEach((item) => {
    const category = item?.category || '未知'
    categoryMap.set(category, (categoryMap.get(category) || 0) + 1)
  })

  const colors = ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444', '#64748b']
  return [...categoryMap.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([name, count], index) => ({
      name,
      count,
      percent: Math.round((count / total) * 100),
      color: colors[index % colors.length],
    }))
}

export function getOpportunityStatusLabel(status) {
  const statusMap = {
    watch: '待研判',
    recommend: '商机推荐',
    converted: '已立项',
  }
  return statusMap[status] || '待研判'
}

export function getOpportunityStatusType(status) {
  const statusTypeMap = {
    watch: 'info',
    recommend: 'success',
    converted: 'warning',
  }
  return statusTypeMap[status] || 'info'
}

export function normalizeConfidence(score) {
  return Math.max(0, Math.min(100, Math.round(Number(score || 0) * 100)))
}

export function getScoreColor(score) {
  const value = Number(score || 0)
  return value >= 80 ? '#10b981' : value >= 60 ? '#f59e0b' : '#64748b'
}

export function getScoreClass(score) {
  const value = Number(score || 0)
  return value >= 80 ? 'high' : value >= 60 ? 'mid' : 'low'
}

export function confidenceColor(value) {
  const normalized = Number(value || 0)
  return normalized >= 80 ? '#10b981' : normalized >= 60 ? '#f59e0b' : '#3b82f6'
}

export function buildDeadlineFromWindow(windowValue, now = new Date()) {
  if (!windowValue) {
    return formatDateTime(addDays(now, 30), '18:00:00')
  }

  const text = String(windowValue)
  const monthMatch = text.match(/^(\d{4})-(\d{2})/)
  if (monthMatch) {
    return `${monthMatch[1]}-${monthMatch[2]}-28T18:00:00`
  }

  return formatDateTime(addDays(now, 30), '18:00:00')
}

function resolveOpportunityTenderId(prediction = {}, customer = {}) {
  const directId = Number(prediction?.tenderId || 0)
  if (directId) {
    return directId
  }

  const evidenceId = Number(Array.isArray(prediction?.evidenceRecords) ? prediction.evidenceRecords[0] : 0)
  if (evidenceId) {
    return evidenceId
  }

  const historyId = Number(Array.isArray(customer?.purchaseHistory) ? customer.purchaseHistory[0]?.recordId : 0)
  return historyId || null
}

function formatDateTime(date, timeSuffix) {
  const target = new Date(date)
  const year = target.getFullYear()
  const month = String(target.getMonth() + 1).padStart(2, '0')
  const day = String(target.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}T${timeSuffix}`
}

function addDays(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

function isWindowWithinMonths(windowValue, months = []) {
  const text = String(windowValue || '')
  const match = text.match(/^(\d{4})-(\d{2})/)
  if (!match) {
    return false
  }
  return months.includes(Number(match[2]))
}
