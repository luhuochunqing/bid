<template>
  <div class="score-analysis-container">
    <div class="page-header">
      <h2>评分分析</h2>
      <el-button type="primary" @click="showForm = true">新建评分</el-button>
    </div>

    <el-card v-if="showForm" class="form-card">
      <h3>多维度评分</h3>
      <el-form :model="form" label-width="140px" style="max-width: 700px">
        <el-form-item label="项目名称">
          <el-input v-model="form.projectName" placeholder="项目名称" />
        </el-form-item>
        <el-form-item v-for="dim in dimensions" :key="dim.dimension" :label="dim.dimension">
          <el-slider v-model="dim.score" :min="0" :max="100" show-input />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="calculateScore">计算</el-button>
          <el-button @click="showForm = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="result" class="result-card">
      <h3>评分结果</h3>
      <el-row :gutter="20">
        <el-col :span="8">
          <div class="score-display">
            <div class="score-value" :class="scoreClass(result.totalScore)">{{ result.totalScore.toFixed(1) }}</div>
            <div class="score-label">综合评分</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="risk-display">
            <el-tag :type="riskType(result.riskLevel)" size="large">{{ result.riskLevel }}</el-tag>
            <div class="risk-label">风险等级</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="recommend-display">
            <el-tag :type="recommendType(result.recommendation)" size="large">{{ result.recommendation }}</el-tag>
            <div class="recommend-label">建议</div>
          </div>
        </el-col>
      </el-row>
      <div class="dimension-chart">
        <h4>维度得分</h4>
        <div v-for="dim in result.dimensions" :key="dim.dimension" class="dimension-row">
          <span class="dim-name">{{ dim.dimension }}</span>
          <el-progress :percentage="dim.score" :color="scoreColor(dim.score)" style="flex: 1;" />
        </div>
      </div>
    </el-card>

    <el-card class="history-card">
      <h3>历史评分</h3>
      <el-table :data="analyses" stripe>
        <el-table-column prop="projectName" label="项目" />
        <el-table-column prop="totalScore" label="总分" width="80" align="center">
          <template #default="{ row }">
            <span :class="scoreClass(row.totalScore)">{{ row.totalScore.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险" width="80">
          <template #default="{ row }">
            <el-tag :type="riskType(row.riskLevel)">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="recommendation" label="建议" width="80">
          <template #default="{ row }">
            <el-tag :type="recommendType(row.recommendation)">{{ row.recommendation }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { scoreAnalysisApi } from '@/api/modules/scoreAnalysis.js'

const loading = ref(false)
const analyses = ref([])
const showForm = ref(false)
const result = ref(null)
const form = reactive({ projectName: '' })
const dimensions = reactive([
  { dimension: '技术能力', score: 70, weight: 1.0 },
  { dimension: '财务实力', score: 70, weight: 1.0 },
  { dimension: '团队经验', score: 70, weight: 1.0 },
  { dimension: '历史业绩', score: 70, weight: 1.0 },
  { dimension: '合规性', score: 70, weight: 1.0 },
  { dimension: '价格竞争力', score: 70, weight: 1.0 },
])

onMounted(() => { loadAnalyses() })

async function loadAnalyses() {
  loading.value = true
  try {
    const res = await scoreAnalysisApi.getAnalyses()
    analyses.value = res.data || []
  } catch (e) {
    ElMessage.error('加载历史评分失败')
  } finally {
    loading.value = false
  }
}

function calculateScore() {
  const totalWeight = dimensions.reduce((sum, d) => sum + d.weight, 0)
  const totalScore = dimensions.reduce((sum, d) => sum + d.score * d.weight, 0) / totalWeight

  const lowCount = dimensions.filter(d => d.score < 40).length

  let riskLevel = 'LOW'
  if (totalScore < 50 || lowCount >= 2) riskLevel = 'HIGH'
  else if (totalScore < 70 || lowCount >= 1) riskLevel = 'MEDIUM'

  let recommendation = 'HOLD'
  if (totalScore >= 80) recommendation = 'STRONG_BUY'
  else if (totalScore >= 70) recommendation = 'BUY'
  else if (totalScore < 50) recommendation = 'AVOID'

  result.value = {
    projectName: form.projectName,
    totalScore,
    riskLevel,
    recommendation,
    dimensions: dimensions.map(d => ({ dimension: d.dimension, score: d.score, weight: d.weight })),
  }
  ElMessage.success('计算完成')
}

function scoreClass(score) {
  if (score >= 80) return 'score-high'
  if (score >= 60) return 'score-medium'
  return 'score-low'
}

function scoreColor(score) {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

function riskType(level) {
  const map = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' }
  return map[level] || 'info'
}

function recommendType(rec) {
  const map = { STRONG_BUY: 'success', BUY: 'primary', HOLD: 'warning', AVOID: 'danger' }
  return map[rec] || 'info'
}
</script>

<style scoped>
.score-analysis-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
.form-card, .result-card, .history-card { margin-bottom: 20px; }
.result-card h3, .history-card h3 { margin: 0 0 16px 0; }
.score-display, .risk-display, .recommend-display { text-align: center; }
.score-value { font-size: 48px; font-weight: bold; }
.score-high { color: #67c23a; }
.score-medium { color: #e6a23c; }
.score-low { color: #f56c6c; }
.dimension-row { display: flex; align-items: center; margin-bottom: 12px; }
.dim-name { width: 120px; }
</style>
