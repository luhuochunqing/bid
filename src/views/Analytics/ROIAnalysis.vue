<template>
  <div class="roi-analysis-container">
    <div class="page-header">
      <h2>ROI分析</h2>
      <el-button type="primary" @click="showForm = true">新建分析</el-button>
    </div>

    <el-card v-if="showForm" class="form-card">
      <h3>投入产出分析</h3>
      <el-form :model="form" label-width="120px" style="max-width: 600px">
        <el-form-item label="项目名称">
          <el-input v-model="form.projectName" placeholder="项目名称" />
        </el-form-item>
        <el-form-item label="总投资(万元)">
          <el-input-number v-model="form.investment" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="预期收入(万元)">
          <el-input-number v-model="form.revenue" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="运营成本(万元)">
          <el-input-number v-model="form.operatingCost" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="项目周期(月)">
          <el-input-number v-model="form.periodMonths" :min="1" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="calculateROI">计算</el-button>
          <el-button @click="showForm = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="result" class="result-card">
      <h3>分析结果</h3>
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="metric">
            <div class="metric-label">ROI</div>
            <div class="metric-value" :class="result.roiPercentage >= 0 ? 'positive' : 'negative'">
              {{ result.roiPercentage.toFixed(2) }}%
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="metric-label">利润(万元)</div>
            <div class="metric-value">{{ result.profit.toFixed(2) }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="metric-label">回本周期</div>
            <div class="metric-value">{{ result.paybackMonths.toFixed(1) }} 月</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="metric-label">风险等级</div>
            <div class="metric-value">
              <el-tag :type="riskType(result.riskLevel)">{{ result.riskLevel }}</el-tag>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card class="history-card">
      <h3>历史分析</h3>
      <el-table :data="analyses" stripe>
        <el-table-column prop="projectName" label="项目" />
        <el-table-column prop="investment" label="投资(万)" width="100" align="center" />
        <el-table-column prop="revenue" label="收入(万)" width="100" align="center" />
        <el-table-column prop="roiPercentage" label="ROI%" width="100" align="center">
          <template #default="{ row }">
            <span :class="row.roiPercentage >= 0 ? 'positive' : 'negative'">{{ row.roiPercentage.toFixed(2) }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="paybackMonths" label="回本(月)" width="100" align="center" />
        <el-table-column prop="riskLevel" label="风险" width="80">
          <template #default="{ row }">
            <el-tag :type="riskType(row.riskLevel)">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { roiApi } from '@/api/modules/roi.js'

const loading = ref(false)
const analyses = ref([])
const showForm = ref(false)
const result = ref(null)
const form = reactive({ projectName: '', investment: 0, revenue: 0, operatingCost: 0, periodMonths: 12 })

onMounted(() => { loadAnalyses() })

async function loadAnalyses() {
  loading.value = true
  try {
    const res = await roiApi.getAnalyses()
    analyses.value = res.data || []
  } catch (e) {
    ElMessage.error('加载历史分析失败')
  } finally {
    loading.value = false
  }
}

function calculateROI() {
  const investment = Number(form.investment) || 0
  const revenue = Number(form.revenue) || 0
  const operatingCost = Number(form.operatingCost) || 0
  const periodMonths = Number(form.periodMonths) || 12

  const profit = revenue - investment - operatingCost
  const roiPercentage = investment > 0 ? (profit / investment) * 100 : 0
  const paybackMonths = profit > 0 ? (investment / (revenue - operatingCost)) * periodMonths : 0

  let riskLevel = 'LOW'
  if (roiPercentage < 0) riskLevel = 'HIGH'
  else if (roiPercentage < 10) riskLevel = 'MEDIUM'

  result.value = {
    projectName: form.projectName,
    investment,
    revenue,
    operatingCost,
    profit,
    roiPercentage,
    paybackMonths,
    riskLevel,
  }

  ElMessage.success('计算完成')
}

function riskType(level) {
  const map = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' }
  return map[level] || 'info'
}
</script>

<style scoped>
.roi-analysis-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
.form-card, .result-card, .history-card { margin-bottom: 20px; }
.result-card h3, .history-card h3 { margin: 0 0 16px 0; }
.metric { text-align: center; }
.metric-label { font-size: 12px; color: var(--text-muted); margin-bottom: 8px; }
.metric-value { font-size: 24px; font-weight: bold; }
.positive { color: #67c23a; }
.negative { color: #f56c6c; }
</style>
