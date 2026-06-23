// Input: httpClient, API mode config, analytics normalizers and demo adapters
// Output: dashboardApi - dashboard metrics, tasks, and drill-down accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 数据看板与任务模块 API
 * 真实 API 数据看板与任务访问层
 */
import httpClient from '../client.js'

function normalizeTrendItem(item) {
  return {
    month: item?.period || '-',
    bids: Number(item?.count || 0),
    wins: 0,
    rate: Number(item?.changePercentage || 0),
    amount: Number(item?.value || 0) }
}

function normalizeCompetitorItem(item, totalAmount) {
  const amount = Number(item?.totalBidAmount || 0)
  return {
    name: item?.name || '未知竞争对手',
    share: totalAmount > 0 ? Number(((amount / totalAmount) * 100).toFixed(1)) : 0,
    amount,
    bids: Number(item?.bidCount || 0),
    wins: Number(item?.winCount || 0),
    rate: Number(item?.winRate || 0) }
}

function normalizeRegionItem(item) {
  return {
    name: item?.region || '未知区域',
    amount: Number(item?.totalBudget || 0),
    bids: Number(item?.tenderCount || 0),
    rate: Number(item?.percentage || 0) }
}

function normalizeProductLineItem(item) {
  return {
    name: item?.name || '综合解决方案',
    revenue: Number(item?.revenue || 0),
    cost: Number(item?.cost || 0),
    bids: Number(item?.bids || 0),
    rate: Number(item?.rate || 0) }
}

function buildApiOverview(overview = {}) {
  const summary = overview?.summaryStats || {}
  const competitors = Array.isArray(overview?.topCompetitors) ? overview.topCompetitors : []
  const competitorTotalAmount = competitors.reduce((sum, item) => sum + Number(item?.totalBidAmount || 0), 0)

  return {
    totalBids: Number(summary?.totalTenders || 0),
    totalBidsChange: '--',
    inProgress: Number(summary?.activeProjects || 0),
    wonThisYear: 0,
    winRate: Number(summary?.successRate || 0),
    winRateChange: '--',
    totalAmount: Number(summary?.totalBudget || 0),
    totalAmountChange: '--',
    totalCost: 0,
    totalCostChange: '--',
    trendData: Array.isArray(overview?.tenderTrends) ? overview.tenderTrends.map(normalizeTrendItem) : [],
    competitors: competitors.map((item) => normalizeCompetitorItem(item, competitorTotalAmount)),
    productLines: [],
    regionData: Array.isArray(overview?.regionalDistribution) ? overview.regionalDistribution.map(normalizeRegionItem) : [],
    statusDistribution: overview?.statusDistribution || {},
    backendSummary: {
      activeProjects: Number(summary?.activeProjects || 0),
      pendingTasks: Number(summary?.pendingTasks || 0) } }
}

export const dashboardApi = {
  async getOverview() {

    const [overviewResponse, productLineResponse] = await Promise.all([
      httpClient.get('/api/analytics/overview'),
      httpClient.get('/api/analytics/product-lines'),
    ])

    const data = {
      ...buildApiOverview(overviewResponse?.data),
      productLines: Array.isArray(productLineResponse?.data)
        ? productLineResponse.data.map(normalizeProductLineItem)
        : [] }

    return {
      ...overviewResponse,
      data }
  },

  async getStats() {
    return this.getOverview()
  },

  async getTrend() {

    const response = await httpClient.get('/api/analytics/trends')
    const apiData = Array.isArray(response?.data?.tenders) ? response.data.tenders.map(normalizeTrendItem) : []
    return {
      ...response,
      data: apiData }
  },

  async getCompetitors() {

    const response = await httpClient.get('/api/analytics/competitors')
    const competitors = Array.isArray(response?.data) ? response.data : []
    const totalAmount = competitors.reduce((sum, item) => sum + Number(item?.totalBidAmount || 0), 0)
    const data = competitors.map((item) => normalizeCompetitorItem(item, totalAmount))

    return {
      ...response,
      data }
  },

  async getRegionData() {

    const response = await httpClient.get('/api/analytics/regions')
    const apiData = Array.isArray(response?.data) ? response.data.map(normalizeRegionItem) : []
    return {
      ...response,
      data: apiData }
  },

  async getProductLines() {

    const response = await httpClient.get('/api/analytics/product-lines')
    const apiData = Array.isArray(response?.data) ? response.data.map(normalizeProductLineItem) : []
    return {
      ...response,
      data: apiData }
  },

  async getCustomerTypes(params = {}) {
    return httpClient.get('/api/analytics/customer-types', { params: params || {} })
  },

  async getCustomerTypeDrillDown(params = {}) {
    return httpClient.get('/api/analytics/drilldown/customer-type', { params: params || {} })
  },

  async getDrillDown(type, paramsOrKey) {

    const metricTypes = new Set(['revenue', 'win-rate', 'team', 'projects'])
    if (metricTypes.has(type)) {
      const params = (paramsOrKey && typeof paramsOrKey === 'object' && !Array.isArray(paramsOrKey))
        ? paramsOrKey
        : {}
      return httpClient.get(`/api/analytics/drilldown/${type}`, { params })
    }

    return httpClient.get('/api/analytics/drill-down', {
      params: { type, key: paramsOrKey } })
  },

  async getSummary() {
    try {
      const response = await httpClient.get('/api/analytics/summary')
      const raw = response?.data || {}
      return {
        success: true,
        data: {
          totalTenders: Number(raw.totalTenders) || 0,
          activeProjects: Number(raw.activeProjects) || 0,
          pendingTasks: Number(raw.pendingTasks) || 0,
          totalBudget: Number(raw.totalBudget) || 0,
          successRate: Number(raw.successRate) || 0 } }
    } catch {
      return {
        success: false,
        data: {
          totalTenders: 0,
          activeProjects: 0,
          pendingTasks: 0,
          totalBudget: 0,
          successRate: 0 } }
    }
  },

  async getLayout() {
    try {
      const response = await httpClient.get('/api/dashboard/layout/my', { silentAuthError: true })
      return {
        success: response?.success === true,
        data: response?.data || null
      }
    } catch (error) {
      return { success: false, data: null, error }
    }
  },
  
  async getRuntimeMode() {
    const response = await httpClient.get('/api/system/runtime-mode')
    const raw = response?.data || {}
    return {
      success: response?.success === true,
      data: {
        modeCode: String(raw.modeCode || ''),
        modeLabel: String(raw.modeLabel || ''),
        database: String(raw.database || ''),
        demoFusionEnabled: Boolean(raw.demoFusionEnabled),
        activeProfiles: Array.isArray(raw.activeProfiles) ? raw.activeProfiles : [] 
      } 
    }
  }
}

export const tasksApi = {
  async getMine(assigneeId) {
    return httpClient.get('/api/tasks/my', { params: { assigneeId } })
  },

  async getBoardItems() {
    return httpClient.get('/api/task-board/items')
  },

  async getList(params) {
    return httpClient.get('/api/tasks', { params })
  },

  async getDetail(id) {
    return httpClient.get(`/api/tasks/${id}`)
  },

  async create(data) {
    return httpClient.post('/api/tasks', data)
  },

  async update(id, data) {
    return httpClient.put(`/api/tasks/${id}`, data)
  },

  async delete(id) {
    return httpClient.delete(`/api/tasks/${id}`)
  },

  async complete(id) {
    return httpClient.patch(`/api/tasks/${id}/status`, JSON.stringify('COMPLETED'), {
      headers: {
        'Content-Type': 'application/json' } })
  },

  async updateStatus(id, status) {
    return httpClient.patch(`/api/tasks/${id}/status`, JSON.stringify(status), {
      headers: {
        'Content-Type': 'application/json' } })
  } }

export const todosApi = {
  async getList() {
    return Promise.resolve({
      success: false,
      message: 'Todo endpoints are not implemented on the backend yet',
      data: [] })
  },

  async complete(_id) {
    return Promise.resolve({
      success: false,
      message: 'Todo endpoints are not implemented on the backend yet' })
  } }

export default {
  dashboard: dashboardApi,
  tasks: tasksApi,
  todos: todosApi }
