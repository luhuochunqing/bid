<template>
  <div class="case-grid-container">
    <el-card class="filter-card">
      <template #header>
        <div class="card-header-title"><el-icon><Grid /></el-icon><span>AI 案例库网格</span></div>
      </template>
      <el-form :inline="true" :model="filters" class="search-form">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="搜索打分点/需求/应答..." clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="评分项类别">
          <el-select v-model="filters.scoringCategory" placeholder="全部类别" clearable style="width: 150px">
            <el-option label="全部" value="" />
            <el-option v-for="cat in SCORING_CATEGORIES" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户类型">
          <el-select v-model="filters.customerType" placeholder="选择客户类型" clearable style="width: 150px">
            <el-option label="全部" value="" />
            <el-option v-for="(label, val) in CUSTOMER_TYPE_LABELS" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类型">
          <el-select v-model="filters.projectTypes" placeholder="全部类型" clearable multiple collapse-tags style="width: 220px">
            <el-option v-for="(label, val) in PROJECT_TYPE_LABELS" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目状态">
          <el-select v-model="filters.statuses" placeholder="全部状态" clearable multiple collapse-tags style="width: 180px">
            <el-option v-for="(label, val) in STATUS_LABELS" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传时间">
          <el-date-picker v-model="filters.uploadDateRange" type="daterange" range-separator="至"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" clearable />
        </el-form-item>
        <el-form-item label="结项时间">
          <el-date-picker v-model="filters.closeDateRange" type="daterange" range-separator="至"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" clearable />
        </el-form-item>
        <el-form-item label="排序方式">
          <el-radio-group v-model="filters.sortBy" @change="handleSearch">
            <el-radio-button label="created">最新发布</el-radio-button>
            <el-radio-button label="reuse">最热复用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon class="mr-1"><Search /></el-icon>筛选</el-button>
          <el-button @click="handleReset"><el-icon class="mr-1"><Refresh /></el-icon>重置</el-button>
          <el-button type="warning" @click="handleExportZip" :loading="exportLoading"><el-icon class="mr-1"><Download /></el-icon>📦 导出文件包</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="grid-content" v-loading="loading">
            <template v-if="cases.length === 0">
        <el-empty v-if="hasActiveFilters" description="未找到符合条件的案例" />
        <el-empty v-else description="案例库还没有内容。去结项的项目页点击&#39;AI 自动生成案例&#39;" />
      </template>
      <el-row v-else :gutter="20" class="card-grid">
        <el-col v-for="item in cases" :key="item.id || item.caseId" :xs="24" :sm="12" :md="8" :lg="6" class="grid-col">
          <CaseCard :case-data="item" :can-manage="canManage"
            @view-detail="viewDetail" @reuse="handleReuse" />
        </el-col>
      </el-row>
      <div class="pagination-container mt-6" v-if="totalElements > 0">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[8, 16, 24, 48]"
          layout="total, sizes, prev, pager, next, jumper" :total="totalElements"
          @current-change="loadCases" @size-change="handleSizeChange" />
      </div>
    </div>

    <CaseDetailDrawer v-model="drawerVisible" :case-data="selectedCase" :loading="drawerLoading" :can-manage="canManage"
      :related-cases="relatedCases" :reuse-history="reuseHistory"
      @reuse="handleReuseInDrawer" @switch-case="switchCase" @toggle-pin="handleTogglePin" @off-shelf="handleOffShelfClick(selectedCase)"
      @go-source="goToSourceProject" @open-bid="openBidDocument" />

    <el-dialog v-model="offShelfDialogVisible" title="确认下架案例" width="420px" :close-on-click-modal="false">
      <p>确认下架该案例？下架后任何前台界面均不可见，需运维介入恢复。</p>
      <template #footer>
        <el-button @click="offShelfDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="offShelfLoading" @click="confirmOffShelf">确认下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { Grid, Search, Refresh, Link, Document, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import httpClient from '@/api/client.js'
import { casesApi } from '@/api/modules/knowledge.js'
import { useUserStore } from '@/stores/user'
import CaseCard from './components/CaseCard.vue'
import CaseDetailDrawer from './components/CaseDetailDrawer.vue'
import { PROJECT_TYPE_LABELS, CUSTOMER_TYPE_LABELS, SCORING_CATEGORIES, STATUS_LABELS } from './caseLabels.js'

const userStore = useUserStore()
const canManage = computed(() => { const r = userStore.currentUser?.role; return r === 'admin' || r === 'bid_admin' || r === 'bid_lead' })

const filters = reactive({ keyword: '', scoringCategory: '', customerType: '', projectTypes: [], statuses: [], sortBy: 'created', uploadDateRange: [], closeDateRange: [] })
const loading = ref(false); const cases = ref([]); const page = ref(1); const pageSize = ref(16); const totalElements = ref(0)
const drawerVisible = ref(false); const drawerLoading = ref(false); const selectedCase = ref(null); const relatedCases = ref([]); const reuseHistory = ref([])
const offShelfDialogVisible = ref(false); const offShelfTarget = ref(null); const offShelfLoading = ref(false); const exportLoading = ref(false)

const loadCases = async () => {
  loading.value = true
  try {
    const res = await casesApi.getGridList({
      keyword: filters.keyword || undefined,
      scoringCategory: filters.scoringCategory || undefined,
      customerType: filters.customerType || undefined,
      projectTypes: filters.projectTypes.length > 0 ? filters.projectTypes : undefined,
      statuses: filters.statuses.length > 0 ? filters.statuses : undefined,
      sort: filters.sortBy, page: page.value, pageSize: pageSize.value,
      uploadDateFrom: filters.uploadDateRange?.[0] || undefined,
      uploadDateTo: filters.uploadDateRange?.[1] || undefined,
      closeDateFrom: filters.closeDateRange?.[0] || undefined,
      closeDateTo: filters.closeDateRange?.[1] || undefined
    })
    cases.value = Array.isArray(res.data) ? res.data : []
    totalElements.value = res.total || 0
  } catch { ElMessage.error('获取案例列表失败') } finally { loading.value = false }
}

const hasActiveFilters = computed(() => filters.keyword || filters.scoringCategory || filters.customerType || filters.projectTypes.length > 0 || filters.statuses.length > 0 || filters.uploadDateRange.length > 0 || filters.closeDateRange.length > 0)

const handleSearch = () => { page.value = 1; loadCases() }
const handleReset = () => {
  Object.assign(filters, { keyword: '', scoringCategory: '', customerType: '', projectTypes: [], statuses: [], sortBy: 'created', uploadDateRange: [], closeDateRange: [] })
  page.value = 1; loadCases()
}
const handleSizeChange = (val) => { pageSize.value = val; page.value = 1; loadCases() }

const handleExportZip = async () => {
  exportLoading.value = true
  try {
    const blob = await casesApi.exportZip({
      keyword: filters.keyword || undefined,
      scoringCategory: filters.scoringCategory || undefined,
      customerType: filters.customerType || undefined,
      projectTypes: filters.projectTypes.length > 0 ? filters.projectTypes : undefined,
      statuses: filters.statuses.length > 0 ? filters.statuses : undefined,
      sortBy: filters.sortBy,
      uploadDateFrom: filters.uploadDateRange?.[0] || undefined,
      uploadDateTo: filters.uploadDateRange?.[1] || undefined
    })
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

const handleExportExcel = async () => {
  exportLoading.value = true
  try {
    const result = await casesApi.exportExcel({
      keyword: filters.keyword || undefined,
      scoringCategory: filters.scoringCategory || undefined,
      customerType: filters.customerType || undefined,
      projectTypes: filters.projectTypes.length > 0 ? filters.projectTypes : undefined,
      statuses: filters.statuses.length > 0 ? filters.statuses : undefined,
      uploadDateFrom: filters.uploadDateRange?.[0] || undefined,
      uploadDateTo: filters.uploadDateRange?.[1] || undefined,
      closeDateFrom: filters.closeDateRange?.[0] || undefined,
      closeDateTo: filters.closeDateRange?.[1] || undefined
    })
    if (result?.success) {
      ElMessage.success(`台账已导出：${result.filename || '案例库台账.xlsx'}`)
    } else {
      ElMessage.error('导出失败，请重试')
    }
  } catch {
    ElMessage.error('导出失败，请重试')
  } finally {
    exportLoading.value = false
  }
}

const handleReuse = async (item) => {
  try { await navigator.clipboard.writeText(item.responseText || item.summary || ''); await casesApi.reuseCase(item.caseId || item.id); if (item.reuseCount != null) item.reuseCount += 1; ElMessage.success('已复制到剪贴板')
  } catch { ElMessage.error('复制失败') }
}

const handleTogglePin = async (item) => {
  try { const p = item.pinned || item.isPinned; await httpClient.post(`/api/cases/${item.caseId || item.id}/${p ? 'unpin' : 'pin'}`); item.pinned = !p; item.isPinned = !p; ElMessage.success(p ? '已取消置顶' : '已置顶')
  } catch { ElMessage.error('置顶操作失败') }
}

const handleReuseInDrawer = async (d) => {
  try { await navigator.clipboard.writeText(d.responseText || ''); await casesApi.reuseCase(d.caseId || d.id); if (d.reuseCount != null) d.reuseCount += 1; const __mc = cases.value.find(c => (c.caseId || c.id) === (d.caseId || d.id)); if (__mc && __mc !== d && __mc.reuseCount != null) __mc.reuseCount += 1; ElMessage.success('已复制到剪贴板')
  } catch { ElMessage.error('复制失败') }
}

const handleOffShelfClick = (item) => { offShelfTarget.value = item; offShelfDialogVisible.value = true }

const confirmOffShelf = async () => {
  if (!offShelfTarget.value) return; offShelfLoading.value = true
  try { await casesApi.offShelfCase(offShelfTarget.value.caseId || offShelfTarget.value.id); ElMessage.success('案例已下架'); offShelfDialogVisible.value = false; offShelfTarget.value = null; await loadCases()
  } catch { ElMessage.error('下架失败') } finally { offShelfLoading.value = false }
}

const goToSourceProject = (d) => { d.sourceProjectId ? window.open(`/bidding/detail/${d.sourceProjectId}`, '_blank') : ElMessage.warning('来源项目不可用') }
const openBidDocument = async (d) => {
  if (!d?.sourceProjectId) { ElMessage.warning('标书原文暂不可用'); return }
  try {
    const res = await httpClient.get(`/api/archive/export-zip/${d.sourceProjectId}`, { responseType: 'blob' })
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([res], { type: 'application/zip' })); a.download = `标书原文-${d.sourceProjectId}.zip`; a.click(); URL.revokeObjectURL(a.href)
    ElMessage.success('标书原文下载成功')
  } catch (e) { ElMessage.error('标书原文下载失败：' + (e?.response?.data?.msg || e?.message || '未知错误')) }
}

const loadRelated = async (current) => {
  if (!current?.id) return
  try {
    const params = { projectType: current.projectType, page: 1, pageSize: 6 }
    if (current.customerType) params.customerType = current.customerType
    if (current.scoringCategory) params.scoringCategory = current.scoringCategory
    const res = await casesApi.getList(params)
    const items = Array.isArray(res.data) ? res.data : []
    relatedCases.value = items.filter(i => (i.caseId || i.id) !== current.id).slice(0, 5)
  } catch {}
}

const loadReuseHistory = async (caseId) => {
  try { const references = await casesApi.getReferenceRecords(caseId); const items = Array.isArray(references) ? references : (Array.isArray(references?.data) ? references.data : []); reuseHistory.value = items.map(r => ({ time: r.referencedAt || r.createdAt || '', referencedByName: r.referencedByName || '用户', referencedProjectName: r.sourceProjectName || '' }))
  } catch { reuseHistory.value = [] }
}

const viewDetail = async (id) => {
  drawerVisible.value = true; drawerLoading.value = true; selectedCase.value = null; relatedCases.value = []; reuseHistory.value = []
  try { const res = await casesApi.getDetail(id); selectedCase.value = res?.data || res; await Promise.all([loadRelated(selectedCase.value), loadReuseHistory(id)])
  } catch { ElMessage.error('获取案例详情失败') } finally { drawerLoading.value = false }
}

const switchCase = async (id) => {
  drawerLoading.value = true
  try { const res = await casesApi.getDetail(id); selectedCase.value = res?.data || res; await Promise.all([loadRelated(selectedCase.value), loadReuseHistory(id)])
  } catch { ElMessage.error('跳转案例详情失败') } finally { drawerLoading.value = false }
}

const SESSION_KEY = 'case-drawer-state'
const restoreDrawerState = () => {
  try {
    const saved = sessionStorage.getItem(SESSION_KEY)
    if (saved) {
      const state = JSON.parse(saved)
      if (state.caseId) {
        selectedCase.value = state; drawerVisible.value = true
        Promise.all([loadRelated(state), loadReuseHistory(state.caseId || state.id)]).finally(() => { drawerLoading.value = false })
      }
    }
  } catch { /* ignore parse errors */ }
}

// Save drawer state before unmount (tab switch / page navigate)
const saveDrawerState = () => {
  if (drawerVisible.value && selectedCase.value) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(selectedCase.value))
  } else {
    sessionStorage.removeItem(SESSION_KEY)
  }
}

// Also save when drawer visibility changes
watch(drawerVisible, (visible) => {
  if (!visible) {
    sessionStorage.removeItem(SESSION_KEY)
  }
})

onMounted(() => { restoreDrawerState(); loadCases(); })
onBeforeUnmount(saveDrawerState)
</script>

<style scoped>
.case-grid-container { padding: 16px; display: flex; flex-direction: column; gap: 16px; }
.filter-card { border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05); border: 1px solid var(--el-border-color-lighter); }
.card-header-title { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; color: var(--el-text-color-primary); }
.search-form { margin-bottom: -18px; }
.grid-content { min-height: 200px; }
.card-grid { margin-bottom: -20px; }
.grid-col { margin-bottom: 20px; }
.pagination-container { display: flex; justify-content: flex-end; }
.mt-6 { margin-top: 24px; }
.mr-1 { margin-right: 4px; }
.hint-text { font-size: 13px; color: var(--el-text-color-secondary); margin-top: 8px; }
</style>
