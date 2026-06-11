<template>
  <div class="market-timing-page">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">商机时间预测</h2>
      </div>
      <div class="header-actions">
        <el-tag type="info" size="small">至少需要 {{ minCount }} 条历史数据</el-tag>
        <el-button @click="fetchPredictions" :loading="loading">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <div class="summary-bar">
      <el-card class="summary-card"><div class="summary-item"><span class="summary-value">{{ predictions.length }}</span><span class="summary-label">监测业主</span></div></el-card>
      <el-card class="summary-card"><div class="summary-item"><span class="summary-value high-conf">{{ highConfidenceCount }}</span><span class="summary-label">高置信度</span></div></el-card>
      <el-card class="summary-card"><div class="summary-item"><span class="summary-value upcoming">{{ upcomingCount }}</span><span class="summary-label">近期招标</span></div></el-card>
    </div>

    <el-card class="list-card">
      <template #header>
        <div class="list-header">
          <span class="list-title">预测详情</span>
          <el-input v-model="searchKeyword" placeholder="搜索业主名称" clearable style="width: 240px" prefix-icon="Search" />
        </div>
      </template>

      <div v-loading="loading" class="list-body">
        <el-empty v-if="!loading && errorMsg" :description="errorMsg" />
        <el-empty v-else-if="!loading && predictions.length === 0" description="暂无预测数据，请确认系统中有标讯数据">
          <el-button size="small" type="primary" @click="$router.push('/bidding')">去标讯列表看看</el-button>
        </el-empty>

        <div v-else class="prediction-grid">
          <div v-for="item in filteredPredictions" :key="item.purchaserHash" class="prediction-card" :class="{ 'no-data': !item.hasData }">
            <div class="card-top">
              <span class="purchaser-name">{{ item.purchaserName || '未知业主' }}</span>
              <el-tag :type="item.confidence >= 0.8 ? 'success' : item.confidence >= 0.5 ? 'warning' : 'danger'" size="small">{{ item.confidence >= 0.8 ? '高置信' : item.confidence >= 0.5 ? '中置信' : '低置信' }}</el-tag>
            </div>
            <div v-if="item.hasData" class="card-body">
              <div class="next-tender"><span class="label">预计下次招标</span><span class="date">{{ item.nextTenderDate }}</span></div>
              <div class="stats-row">
                <div class="stat"><span class="stat-label">历史条数</span><span class="stat-value">{{ item.historicalCount }}</span></div>
                <div class="stat"><span class="stat-label">置信度</span><span class="stat-value">{{ Math.round(item.confidence * 100) }}%</span></div>
              </div>
              <el-progress :percentage="Math.round(item.confidence * 100)" :color="item.confidence >= 0.8 ? '#67C23A' : item.confidence >= 0.5 ? '#E6A23C' : '#F56C6C'" :show-text="false" class="progress-bar" />
              <p class="note">{{ item.note }}</p>
            </div>
            <div v-else class="card-body no-data"><el-empty description="暂无足够历史数据" :image-size="60" /></div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const predictions = ref([])
const searchKeyword = ref('')
const minCount = ref(2)
const errorMsg = ref('')

function authHeaders() {
  const token = sessionStorage.getItem('token')
  return token ? { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } : { 'Content-Type': 'application/json' }
}

const filteredPredictions = computed(() => {
  if (!searchKeyword.value) return predictions.value
  const kw = searchKeyword.value.toLowerCase()
  return predictions.value.filter(p => (p.purchaserName || '').toLowerCase().includes(kw))
})

const highConfidenceCount = computed(() =>
  predictions.value.filter(p => p.hasData && p.confidence >= 0.7).length
)

const upcomingCount = computed(() => {
  const now = new Date()
  const limit = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000)
  return predictions.value.filter(p => {
    if (!p.hasData || !p.nextTenderDate) return false
    const d = new Date(p.nextTenderDate)
    return d >= now && d <= limit
  }).length
})

const fetchMinCount = async () => {
  try {
    const res = await fetch('/api/market-prediction/config/min-count', { headers: authHeaders() })
    const data = await res.json()
    if (data.success || data.code === 0) minCount.value = data.data || 2
  } catch { /* non-critical */ }
}

const fetchPredictions = async () => {
  loading.value = true; errorMsg.value = ''
  try {
    const tendersRes = await fetch('/api/tenders?page=0&size=200&sort=createdAt,desc', { headers: authHeaders() })
    if (!tendersRes.ok) { errorMsg.value = '获取标讯数据失败'; return }
    const tendersData = await tendersRes.json()
    const tenders = tendersData?.data?.content || tendersData?.data || []
    const hashes = [...new Set(tenders.map(t => t.purchaserHash).filter(Boolean))]

    if (hashes.length === 0) { predictions.value = []; return }

    const res = await fetch('/api/market-prediction/batch', {
      method: 'POST', headers: authHeaders(),
      body: JSON.stringify({ purchaserHashes: hashes.slice(0, 50) })
    })
    if (!res.ok) { errorMsg.value = '预测接口请求失败'; return }
    const data = await res.json()
    if (data.success || data.code === 0) {
      predictions.value = (data.data || []).map(p => ({
        ...p,
        purchaserName: tenders.find(t => t.purchaserHash === p.purchaserHash)?.purchaserName || p.purchaserName || '未知业主'
      }))
    } else { errorMsg.value = data.msg || '获取预测数据失败' }
  } catch (e) { errorMsg.value = '网络请求失败，请检查连接后重试'; predictions.value = [] }
  finally { loading.value = false }
}

onMounted(() => { fetchMinCount(); fetchPredictions() })
</script>

<style scoped>
.market-timing-page { padding: 24px; display: flex; flex-direction: column; gap: 20px; background: var(--bg-subtle); height: 100%; overflow: auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; align-items: center; gap: 16px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; color: var(--text-primary); }
.header-actions { display: flex; align-items: center; gap: 12px; }
.summary-bar { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.summary-card { text-align: center; }
.summary-item { display: flex; flex-direction: column; align-items: center; padding: 8px 0; }
.summary-value { font-size: 32px; font-weight: 700; color: var(--el-color-primary); }
.summary-value.high-conf { color: var(--el-color-success); }
.summary-value.upcoming { color: var(--el-color-warning); }
.summary-label { font-size: 14px; color: var(--el-text-color-secondary); margin-top: 4px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.list-title { font-size: 18px; font-weight: 600; }
.prediction-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 20px; }
.prediction-card { background: var(--el-bg-color); border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 16px; transition: box-shadow .3s; }
.prediction-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,.1); }
.prediction-card.no-data { opacity: .7; }
.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--el-border-color-lighter); }
.purchaser-name { font-size: 16px; font-weight: 600; color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 200px; }
.card-body { margin-bottom: 12px; }
.next-tender { display: flex; flex-direction: column; margin-bottom: 12px; }
.next-tender .label { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 4px; }
.next-tender .date { font-size: 24px; font-weight: 700; color: var(--el-color-primary); }
.stats-row { display: flex; gap: 24px; margin-bottom: 12px; }
.stat { display: flex; flex-direction: column; }
.stat-label { font-size: 12px; color: var(--el-text-color-secondary); }
.stat-value { font-size: 16px; font-weight: 600; color: var(--el-text-color-primary); }
.progress-bar { margin-bottom: 12px; }
.note { font-size: 12px; color: var(--el-text-color-regular); }
</style>
