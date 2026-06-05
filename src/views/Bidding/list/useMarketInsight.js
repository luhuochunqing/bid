// Input: marketInsightApi
// Output: market insight dialog state and refresh actions
// Pos: src/views/Bidding/list/ - Market insight composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { marketInsightApi } from '@/api/modules/marketInsight.js'

const DEFAULT_INDUSTRY_TRENDS = [
  { industry: '劳保安全', count: 413, amount: 37600, growth: 42, trend: 'up', hotLevel: 5, color: 'red' },
  { industry: '工控低压', count: 333, amount: 57600, growth: 30, trend: 'up', hotLevel: 5, color: 'blue' },
  { industry: '电工照明', count: 410, amount: 36300, growth: 24, trend: 'up', hotLevel: 4, color: 'yellow' },
  { industry: '制冷暖通', count: 223, amount: 53300, growth: 25, trend: 'up', hotLevel: 4, color: 'cyan' },
]

const DEFAULT_OPPORTUNITIES = [
  { id: 'base-1', title: '制造业工厂劳保用品年度采购', purchaser: '制造业客户', budget: 680, region: '华东', priority: 'high', match: 95, reason: '需求稳定，产品线匹配度高。' },
  { id: 'base-2', title: '变电站检修工具采购', purchaser: '能源客户', budget: 520, region: '华北', priority: 'high', match: 92, reason: '检修场景明确，工具耗材需求集中。' },
]

const toNumber = (value, fallback = 0) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

const normalizeIndustryTrend = (trend = {}) => ({
  industry: trend.industry || '未分类',
  count: toNumber(trend.count),
  amount: toNumber(trend.amount) / 10000,
  growth: toNumber(trend.growth),
  trend: trend.trend || 'stable',
  hotLevel: toNumber(trend.hotLevel, 1),
  color: trend.color || 'blue',
})

const normalizePotentialOpportunity = (pattern = {}, index) => {
  const opportunity = toNumber(pattern.opportunity || pattern.frequency, 1)
  const match = Math.min(99, Math.max(70, Math.round(78 + opportunity * 7)))
  const industry = pattern.industry || '综合'
  const purchaser = pattern.name || '未知采购方'
  return {
    id: `${purchaser}-${index}`,
    title: `${industry}类近期采购机会`,
    purchaser,
    budget: toNumber(pattern.avgBudget),
    region: pattern.period || '近期',
    priority: opportunity >= 2 ? 'high' : 'medium',
    match,
    reason: `${industry}类采购频次 ${toNumber(pattern.frequency)} 次，建议持续跟进。`,
  }
}

const buildIndustryInsight = (trends) => {
  if (!trends.length) return '暂无实时市场洞察数据'
  const topIndustries = trends.slice(0, 3).map((trend) => trend.industry).join('、')
  return `${topIndustries}需求热度靠前，建议结合预算规模和区域资源优先跟进。`
}

export function useMarketInsight() {
  const showMarketInsight = ref(false)
  const activeInsightTab = ref('industry')
  const loadingTrendData = ref(false)
  const trendDataLoaded = ref(false)
  const industryTrends = ref([...DEFAULT_INDUSTRY_TRENDS])
  const potentialOpportunities = ref([...DEFAULT_OPPORTUNITIES])
  const industryInsight = ref('劳保安全、工控低压、电工照明保持高热度，建议优先关注华东和华北区域。')
  const forecastTips = ref([
    { text: '劳保安全类产品预计 Q2 需求旺盛，建议提前备货。', color: '#67c23a' },
    { text: '工控低压类产品在新能源行业需求强劲，建议重点跟进。', color: '#409eff' },
  ])

  const loadTrendRadarData = async () => {
    if (trendDataLoaded.value) return
    loadingTrendData.value = true
    try {
      const response = await marketInsightApi.getInsight()
      const insight = response?.data || {}
      const trends = Array.isArray(insight.industryTrends) ? insight.industryTrends.map(normalizeIndustryTrend) : []
      const opportunities = Array.isArray(insight.purchaserPatterns)
        ? insight.purchaserPatterns.map(normalizePotentialOpportunity)
        : []
      const tips = Array.isArray(insight.forecastTips) ? insight.forecastTips : []

      industryTrends.value = trends
      potentialOpportunities.value = opportunities
      forecastTips.value = tips
      industryInsight.value = buildIndustryInsight(trends)
      if (trends.length > 0) {
        ElMessage.success(`已加载 ${trends.length} 条市场洞察数据`)
      } else {
        ElMessage.info('暂无实时市场洞察数据')
      }
      trendDataLoaded.value = true
    } catch {
      industryTrends.value = []
      potentialOpportunities.value = []
      forecastTips.value = []
      industryInsight.value = '市场洞察加载失败，请稍后重试'
      ElMessage.error('市场洞察加载失败，请稍后重试')
      trendDataLoaded.value = true
    } finally {
      loadingTrendData.value = false
    }
  }

  const refreshTrendData = async () => {
    trendDataLoaded.value = false
    await loadTrendRadarData()
  }

  watch(showMarketInsight, (visible) => {
    if (visible) loadTrendRadarData()
  })

  return {
    showMarketInsight,
    activeInsightTab,
    loadingTrendData,
    industryTrends,
    potentialOpportunities,
    industryInsight,
    forecastTips,
    refreshTrendData,
  }
}
