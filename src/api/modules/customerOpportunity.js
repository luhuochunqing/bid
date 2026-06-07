// Input: httpClient and customer opportunity endpoints
// Output: customerOpportunityApi and view-model hook backed by real HTTP
// Pos: src/api/modules/ - Feature API module for customer opportunity center
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import httpClient from '../client.js'

export function normalizeCustomerInsight(item = {}) {
  return {
    customerId: item?.customerId || item?.purchaserHash || '',
    customerName: item?.customerName || item?.purchaserName || '未命名客户',
    region: item?.region || '',
    industry: item?.industry || '',
    salesRep: item?.salesRep || '',
    opportunityScore: Number(item?.opportunityScore || 0),
    predictedNextWindow: item?.predictedNextWindow || item?.predictedWindow || '',
    status: item?.status || 'watch',
    mainCategories: Array.isArray(item?.mainCategories) ? item.mainCategories : [],
    avgBudget: Number(item?.avgBudget || 0),
    cycleType: item?.cycleType || '',
  }
}

export function normalizeCustomerPurchase(item = {}) {
  return {
    recordId: item?.recordId ?? item?.id ?? '',
    customerId: item?.customerId || item?.purchaserHash || '',
    publishDate: item?.publishDate || item?.createdAt || '',
    title: item?.title || '',
    category: item?.category || '',
    budget: Number(item?.budget || 0),
    isKey: item?.isKey === true,
    extractedTags: Array.isArray(item?.extractedTags) ? item.extractedTags : [],
  }
}

export function normalizeCustomerPrediction(item = {}) {
  return {
    opportunityId: item?.opportunityId ?? item?.id ?? '',
    customerId: item?.customerId || item?.purchaserHash || '',
    suggestedProjectName: item?.suggestedProjectName || '待智能研判',
    predictedCategory: item?.predictedCategory || '---',
    predictedBudgetMin: Number(item?.predictedBudgetMin || 0),
    predictedBudgetMax: Number(item?.predictedBudgetMax || 0),
    predictedWindow: item?.predictedWindow || '待判断',
    confidence: Number(item?.confidence || 0),
    reasoningSummary: item?.reasoningSummary || '当前数据不足，暂无法生成高置信度预测。',
    evidenceRecords: Array.isArray(item?.evidenceRecords)
      ? item.evidenceRecords.map((record) => Number(record)).filter((record) => Number.isFinite(record))
      : [],
    convertedProjectId: item?.convertedProjectId || null,
  }
}

function normalizeListResponse(response, normalizer) {
  const payload = Array.isArray(response?.data) ? response.data : []
  return {
    ...response,
    data: payload.map(normalizer),
  }
}

export const customerOpportunityApi = {
  async getCustomerInsights(params = {}) {
    const response = await httpClient.get('/api/customer-opportunities/insights', { params })
    return normalizeListResponse(response, normalizeCustomerInsight)
  },

  async getPurchaseHistory(purchaserHash) {
    const response = await httpClient.get(`/api/customer-opportunities/${purchaserHash}/purchases`)
    return normalizeListResponse(response, normalizeCustomerPurchase)
  },

  async getPredictions(purchaserHash) {
    const response = await httpClient.get(`/api/customer-opportunities/${purchaserHash}/predictions`)
    return normalizeListResponse(response, normalizeCustomerPrediction)
  },

  async refreshInsights() {
    return httpClient.post('/api/customer-opportunities/refresh')
  },

  async updatePredictionStatus(id, status) {
    return httpClient.put(`/api/customer-opportunities/predictions/${id}/status`, { status })
  },

  async convertToProject(id, projectId = null) {
    return httpClient.put(`/api/customer-opportunities/predictions/${id}/convert`, { projectId })
  },
}

export function useCustomerOpportunityCenterData() {
  const customerInsights = ref([])
  const customerPurchases = ref([])
  const customerPredictions = ref([])
  const loading = ref(false)

  const salesUsers = computed(() => {
    const unique = new Map()
    customerInsights.value.forEach((item) => {
      if (!item?.salesRep) {
        return
      }
      unique.set(item.salesRep, {
        id: item.salesRep,
        name: item.salesRep,
      })
    })
    return [...unique.values()]
  })

  const loadInsights = async (params = {}) => {
    loading.value = true
    try {
      const response = await customerOpportunityApi.getCustomerInsights(params)
      customerInsights.value = response.data || []
      return response.data || []
    } finally {
      loading.value = false
    }
  }

  const loadCustomerDetail = async (customerId) => {
    const [purchasesResponse, predictionsResponse] = await Promise.all([
      customerOpportunityApi.getPurchaseHistory(customerId),
      customerOpportunityApi.getPredictions(customerId),
    ])

    customerPurchases.value = [
      ...customerPurchases.value.filter((item) => item.customerId !== customerId),
      ...(purchasesResponse.data || []),
    ]
    customerPredictions.value = [
      ...customerPredictions.value.filter((item) => item.customerId !== customerId),
      ...(predictionsResponse.data || []),
    ]

    return {
      purchases: purchasesResponse.data || [],
      predictions: predictionsResponse.data || [],
    }
  }

  return {
    loading,
    customerInsights,
    customerPurchases,
    customerPredictions,
    salesUsers,
    loadInsights,
    loadCustomerDetail,
    refreshInsights: customerOpportunityApi.refreshInsights,
    convertToProject: customerOpportunityApi.convertToProject,
    updatePredictionStatus: customerOpportunityApi.updatePredictionStatus,
  }
}

export default customerOpportunityApi
