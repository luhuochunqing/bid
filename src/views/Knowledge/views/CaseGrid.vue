<template>
  <div class="case-grid-container">
    <div class="kb-page-header">
      <h2>案例库</h2>
      <div class="kb-header-actions">
        <el-button type="warning" @click="handleExportZip" :loading="exportLoading" :disabled="totalElements === 0">导出文件包</el-button>
      </div>
    </div>
    <el-card class="filter-card kb-filter-card">
      <el-form :inline="true" :model="filters" class="search-form">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="搜索评分项/要素..." clearable style="width: 320px" />
        </el-form-item>
        <el-form-item label="客户类型">
          <el-select v-model="filters.customerType" placeholder="全部类型" clearable style="width: 150px">
            <el-option v-for="opt in CUSTOMER_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类型">
          <el-select v-model="filters.projectType" placeholder="全部类型" clearable style="width: 150px">
            <el-option v-for="(label, val) in PROJECT_TYPE_LABELS" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="grid-content" v-loading="loading">
            <template v-if="cases.length === 0">
        <el-empty v-if="hasActiveFilters" description="未找到符合条件的案例"><el-button size="small" @click="handleReset">重置筛选</el-button></el-empty>
        <el-empty v-else description="案例库还没有内容。去结项的项目页点击&#39;AI 自动生成案例&#39;"><el-button type="primary" size="small" @click="$router.push('/project')">去项目页看看</el-button></el-empty>
      </template>
      <el-row v-else :gutter="20" class="card-grid">
        <el-col v-for="item in cases" :key="item.id || item.caseId" :xs="24" :sm="12" :md="8" :lg="6" class="grid-col">
          <CaseCard :case-data="item" @view-detail="viewDetail" @reuse="handleReuse" />
        </el-col>
      </el-row>
      <div class="kb-pagination-wrap" v-if="totalElements > 0">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[8, 16, 24, 48]"
          layout="total, sizes, prev, pager, next, jumper" :total="totalElements"
          @current-change="loadCases" @size-change="handleSizeChange" />
      </div>
    </div>

    <CaseDetailDialog v-model="dialogVisible" :case-data="selectedCase" :loading="dialogLoading"
      @reuse="handleReuseInDialog" @go-source="goToSourceProject" @open-bid="openBidDocument" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { casesApi } from '@/api/modules/knowledge.js'
import { useBidCaseSourceActions } from './useBidCaseSourceActions.js'
import CaseCard from './components/CaseCard.vue'
import CaseDetailDialog from './components/CaseDetailDialog.vue'
import { PROJECT_TYPE_LABELS, CUSTOMER_TYPE_OPTIONS } from './caseLabels.js'

const filters = reactive({ keyword: '', customerType: '', projectType: '' })
const loading = ref(false); const cases = ref([]); const page = ref(1); const pageSize = ref(16); const totalElements = ref(0)
const dialogVisible = ref(false); const dialogLoading = ref(false); const selectedCase = ref(null)
const exportLoading = ref(false)

const buildFilterParams = () => ({
  keyword: filters.keyword || undefined,
  customerType: filters.customerType || undefined,
  projectTypes: filters.projectType ? [filters.projectType] : undefined
})

const loadCases = async () => {
  loading.value = true
  try {
    const res = await casesApi.getGridList({
      ...buildFilterParams(),
      page: page.value, pageSize: pageSize.value
    })
    cases.value = Array.isArray(res.data) ? res.data : []
    totalElements.value = res.total || 0
  } catch { ElMessage.error('获取案例列表失败') } finally { loading.value = false }
}

const hasActiveFilters = computed(() => filters.keyword || filters.customerType || filters.projectType)

const handleSearch = () => { page.value = 1; loadCases() }
const handleReset = () => {
  Object.assign(filters, { keyword: '', customerType: '', projectType: '' })
  page.value = 1; loadCases()
}
const handleSizeChange = (val) => { pageSize.value = val; page.value = 1; loadCases() }

const handleExportZip = async () => {
  exportLoading.value = true
  try {
    const blob = await casesApi.exportZip({ ...buildFilterParams(), sortBy: 'created' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `方案管理-案例库文件包-${new Date().toISOString().replace(/[-:T]/g, '').slice(0, 12)}.zip`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success('导出文件包成功')
  } catch (e) {
    console.error('Export failed:', e)
    ElMessage.error('导出文件包失败')
  } finally {
    exportLoading.value = false
  }
}

const handleReuse = async (item) => {
  try { await navigator.clipboard.writeText(item.responseText || item.summary || ''); await casesApi.reuseCase(item.caseId || item.id); ElMessage.success('已复制到剪贴板')
  } catch { ElMessage.error('复制失败') }
}

const handleReuseInDialog = async (d) => {
  try { await navigator.clipboard.writeText(d.responseText || ''); await casesApi.reuseCase(d.caseId || d.id); ElMessage.success('已复制到剪贴板')
  } catch { ElMessage.error('复制失败') }
}

// 查看源项目 / 标书原文：共享 composable（路由修正 + 未归档预检 + 假 ZIP 防护，详见 useBidCaseSourceActions.js）
const { goToSourceProject, openBidDocument } = useBidCaseSourceActions()

const viewDetail = async (id) => {
  dialogVisible.value = true; dialogLoading.value = true; selectedCase.value = null
  try { const res = await casesApi.getDetail(id); selectedCase.value = res?.data || res
  } catch { ElMessage.error('获取案例详情失败'); dialogVisible.value = false } finally { dialogLoading.value = false }
}

const SESSION_KEY = 'case-detail-state'
const restoreDialogState = () => {
  try {
    const saved = sessionStorage.getItem(SESSION_KEY)
    if (saved) {
      const state = JSON.parse(saved)
      if (state.caseId) {
        selectedCase.value = state; dialogVisible.value = true
      }
    }
  } catch { /* ignore parse errors */ }
}

// Save dialog state before unmount (tab switch / page navigate)
const saveDialogState = () => {
  if (dialogVisible.value && selectedCase.value) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(selectedCase.value))
  } else {
    sessionStorage.removeItem(SESSION_KEY)
  }
}

// Also save when dialog visibility changes
watch(dialogVisible, (visible) => {
  if (!visible) {
    sessionStorage.removeItem(SESSION_KEY)
  }
})

onMounted(() => { restoreDialogState(); loadCases(); })
onBeforeUnmount(saveDialogState)
</script>

<style scoped lang="scss">
@use '../_knowledge-utils' as *;
.case-grid-container { display: flex; flex-direction: column; gap: 16px; }
.search-form { margin-bottom: -18px; } .grid-content { min-height: 200px; } .card-grid { margin-bottom: -20px; } .grid-col { margin-bottom: 20px; }
</style>
