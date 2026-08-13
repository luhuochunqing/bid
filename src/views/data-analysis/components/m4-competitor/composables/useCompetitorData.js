import { ref, computed, watch, nextTick, markRaw } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/modules/dashboard.js'
import { renderDefaultMode, renderGroupedMode } from '../chartRenderer.js'

export function useCompetitorData(chartRef) {
  const loading = ref(false)
  const error = ref(false)
  const noData = ref(false)
  const searchLoading = ref(false)
  const entityLoading = ref(false)
  let chartInstance = null

  const dateRange = ref(null)
  const selectedCompetitors = ref([])
  const selectedEntities = ref([])
  const competitorOptions = ref([])
  const entityOptions = ref([])
  const allCompetitors = ref([])
  const allEntities = ref([])

  const chartData = ref(null)
  const lastRemovedCompetitor = ref(null)

  const isGroupedMode = computed(() => selectedEntities.value.length > 0)

  const searchCompetitors = (query) => {
    if (!query) {
      competitorOptions.value = [...allCompetitors.value]
      return
    }
    competitorOptions.value = allCompetitors.value.filter((c) => c.includes(query))
  }

  const searchEntities = (query) => {
    if (!query) {
      entityOptions.value = [...allEntities.value]
      return
    }
    entityOptions.value = allEntities.value.filter((e) => e.includes(query))
  }

  const onCompetitorChange = (val) => {
    if (!val || val.length === 0) {
      if (lastRemovedCompetitor.value) {
        selectedCompetitors.value = [lastRemovedCompetitor.value]
      }
    }
  }

  watch(selectedCompetitors, (newVal, oldVal) => {
    if (oldVal && newVal && oldVal.length > 1 && newVal.length < oldVal.length) {
      const removed = oldVal.find((item) => !newVal.includes(item))
      if (removed) lastRemovedCompetitor.value = removed
    }
  })

  const renderChart = () => {
    if (!chartRef.value || !chartData.value) return

    nextTick(() => {
      if (!chartInstance) {
        chartInstance = markRaw(echarts.init(chartRef.value))
      }

      const ok = isGroupedMode.value
        ? renderGroupedMode(chartInstance, chartData.value, selectedCompetitors.value, selectedEntities.value)
        : renderDefaultMode(chartInstance, chartData.value, selectedCompetitors.value)

      if (!ok) {
        noData.value = true
      }
      loading.value = false
    })
  }

  const fetchData = async () => {
    if (!selectedCompetitors.value || selectedCompetitors.value.length === 0) return

    loading.value = true
    error.value = false
    noData.value = false

    try {
      const params = { competitors: selectedCompetitors.value }
      if (selectedEntities.value.length > 0) {
        params.tenderEntities = selectedEntities.value
      }
      if (dateRange.value && dateRange.value.length === 2) {
        params.startDate = dateRange.value[0]
        params.endDate = dateRange.value[1]
      }

      const response = await dashboardApi.getCompetitorAnalysis(params)
      chartData.value = response?.data || response || {}

      if (!chartData.value || Object.keys(chartData.value).length === 0) {
        noData.value = true
        loading.value = false
        return
      }

      renderChart()
    } catch (err) {
      console.error('M4 CompetitorAnalysis fetch error:', err)
      error.value = true
      loading.value = false
    }
  }

  const onEntityChange = () => {
    fetchData()
  }

  const initOptions = async () => {
    try {
      const entityRes = await dashboardApi.getTenderEntities()
      const entities = Array.isArray(entityRes?.data)
        ? entityRes.data
        : (Array.isArray(entityRes) ? entityRes : [])

      allEntities.value = entities.map((e) => {
        if (typeof e === 'string') return e
        return e?.name || e?.entityName || ''
      }).filter(Boolean)

      entityOptions.value = [...allEntities.value]
    } catch (err) {
      console.warn('M4 TenderEntities fetch error (non-fatal):', err)
      allEntities.value = []
      entityOptions.value = []
    }
  }

  const resizeChart = () => {
    if (chartInstance) chartInstance.resize()
  }

  const refresh = () => {
    fetchData()
  }

  const disposeChart = () => {
    if (chartInstance) {
      chartInstance.dispose()
      chartInstance = null
    }
  }

  return {
    loading, error, noData, searchLoading, entityLoading,
    dateRange, selectedCompetitors, selectedEntities,
    competitorOptions, entityOptions, allCompetitors, allEntities,
    chartData, isGroupedMode,
    searchCompetitors, searchEntities,
    onCompetitorChange, onEntityChange,
    fetchData, initOptions, resizeChart, refresh, disposeChart
  }
}