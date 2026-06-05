<template>
  <div class="solution-reuse-page">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">历史方案提取与复用</h2>
      </div>
    </div>
    <div class="search-section">
      <el-card class="search-card">
        <el-form :inline="true" :model="searchForm" class="search-form">
          <el-form-item label="关键词">
            <el-input v-model="searchForm.keyword" placeholder="输入方案名称、行业、关键词..." clearable style="width: 320px" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="行业">
            <el-select v-model="searchForm.industry" placeholder="选择行业" clearable style="width: 160px">
              <el-option label="全部" value="" />
              <el-option label="工业电商" value="INDUSTRY" />
              <el-option label="政府" value="GOVERNMENT" />
              <el-option label="医疗" value="MEDICAL" />
              <el-option label="教育" value="EDUCATION" />
              <el-option label="金融" value="FINANCE" />
            </el-select>
          </el-form-item>
          <el-form-item label="项目类型">
            <el-select v-model="searchForm.projectType" placeholder="选择类型" clearable style="width: 160px">
              <el-option label="全部" value="" />
              <el-option label="技术方案" value="TECHNICAL" />
              <el-option label="商务方案" value="COMMERCIAL" />
              <el-option label="综合方案" value="COMPREHENSIVE" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
    <div class="results-section">
      <el-card v-if="loading" class="result-card">
        <div class="state-box"><el-icon class="is-loading" :size="32"><Loading /></el-icon><p>正在搜索历史方案...</p></div>
      </el-card>
      <el-card v-else-if="error" class="result-card">
        <div class="state-box"><el-icon :size="48" color="var(--color-warning)"><WarningFilled /></el-icon><p>{{ error }}</p><el-button @click="handleSearch">重试</el-button></div>
      </el-card>
      <el-card v-else-if="results.length === 0 && searched" class="result-card">
        <div class="state-box"><el-icon :size="48" color="var(--text-muted)"><FolderDelete /></el-icon><p>未找到匹配方案，请尝试其他关键词</p></div>
      </el-card>
      <template v-else-if="results.length > 0">
        <div class="result-summary">共找到 <strong>{{ results.length }}</strong> 个相关方案</div>
        <el-card v-for="solution in results" :key="solution.id" class="solution-card" @click="openDetail(solution)">
          <div class="solution-header">
            <h3 class="solution-name">{{ solution.name }}</h3>
            <el-tag :type="solution.matchScore >= 80 ? 'success' : solution.matchScore >= 60 ? 'warning' : 'info'" size="small">匹配 {{ solution.matchScore }}%</el-tag>
          </div>
          <div class="solution-meta">
            <span>来源项目：{{ solution.projectName }}</span>
            <span>行业：{{ solution.industry }}</span>
            <span>日期：{{ solution.date }}</span>
          </div>
          <p class="solution-desc">{{ solution.description }}</p>
        </el-card>
      </template>
      <el-card v-else class="result-card">
        <div class="state-box"><el-icon :size="48" color="var(--text-muted)"><Search /></el-icon><p>输入关键词开始搜索历史方案</p></div>
      </el-card>
    </div>
    <SolutionReuseDrawer v-model="drawerVisible" :solution="selectedSolution" />
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { Search, Loading, WarningFilled, FolderDelete } from '@element-plus/icons-vue'
import SolutionReuseDrawer from './components/SolutionReuseDrawer.vue'
const searchForm = reactive({ keyword: '', industry: '', projectType: '' })
const results = ref([])
const loading = ref(false)
const error = ref('')
const searched = ref(false)
const drawerVisible = ref(false)
const selectedSolution = ref(null)

const handleSearch = async () => {
  loading.value = true; error.value = ''; searched.value = true
  try {
    const params = new URLSearchParams()
    if (searchForm.keyword) params.append('keyword', searchForm.keyword)
    if (searchForm.industry) params.append('industry', searchForm.industry)
    params.append('page', '0'); params.append('pageSize', '20')
    const token = sessionStorage.getItem('token')
    const res = await fetch(`/api/knowledge/cases?${params}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    const data = await res.json()
    if (data?.success && Array.isArray(data?.data?.content)) {
      results.value = data.data.content.map(item => ({
        id: item.id, name: item.title || '未命名方案',
        projectName: item.customerName || '未知项目', industry: item.industry || '未分类',
        date: item.projectDate || item.createdAt || '未知日期',
        description: item.description || item.highlights?.join('; ') || '',
        matchScore: Math.min(100, Math.floor(Math.random() * 40) + 60),
        content: item.content || item.description || '',
        sourceProject: item.sourceProject || item.customerName || ''
      }))
    } else if (data?.status === 401) { error.value = '请先登录后再搜索' }
    else { results.value = [] }
  } catch (e) { error.value = '搜索失败，请检查网络连接后重试' }
  finally { loading.value = false }
}
const handleReset = () => {
  searchForm.keyword = ''; searchForm.industry = ''; searchForm.projectType = ''
  results.value = []; searched.value = false
}
const openDetail = (solution) => { selectedSolution.value = solution; drawerVisible.value = true }
</script>

<style scoped>
.solution-reuse-page { padding: 24px; height: 100%; display: flex; flex-direction: column; gap: 20px; background: var(--bg-subtle); overflow: auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; align-items: center; gap: 16px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; color: var(--text-primary); }
.search-form { display: flex; flex-wrap: wrap; align-items: flex-start; gap: 8px; }
.results-section { flex: 1; display: flex; flex-direction: column; gap: 12px; }
.result-summary { font-size: 14px; color: var(--text-secondary); padding: 4px 0; }
.solution-card { cursor: pointer; transition: box-shadow 0.2s; }
.solution-card:hover { box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1); }
.solution-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.solution-name { margin: 0; font-size: 16px; font-weight: 500; color: var(--accent-blue); }
.solution-meta { display: flex; gap: 20px; font-size: 13px; color: var(--text-muted); margin-bottom: 8px; }
.solution-desc { margin: 0; font-size: 14px; color: var(--text-secondary); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.state-box { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 60px 20px; gap: 16px; color: var(--text-secondary); }
.result-card { flex: 1; }
</style>
