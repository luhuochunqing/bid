<template>
  <div class="project-list-container">
    <ProjectSearchCard
      :searchForm="searchForm"
      :sourceOptions="sourceOptions"
      :statusOptions="statusOptions"
      :stageOptions="stageOptions"
      :priorityOptions="priorityOptions"
      :projectTypeOptions="projectTypeOptions"
      :customerTypeOptions="customerTypeOptions"
      :bidMonthOptions="bidMonthOptions"
      :chinaRegionOptions="chinaRegionOptions"
      :userOptions="userOptions"
      :userLoading="userLoading"
      :searchUsers="searchUsers"
      @search="onSearch"
      @reset="onReset"
    />
    <el-card class="table-card b2b-section-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">投标项目列表</span>
          <div class="header-actions">
            <el-button size="small" :icon="Download" :loading="exporting" @click="handleExport">导出 Excel</el-button>
            <el-dropdown trigger="click" @command="toggleColumn">
              <el-button size="small">
                列设置 <span class="col-count-badge">{{ visibleOptionalCount }}/{{ columnOptions.length }}</span>
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="col in columnOptions" :key="col.key" :command="col.key">
                    <el-icon v-if="columnVisible[col.key]" style="color:var(--el-color-primary)"><Check /></el-icon>
                    <span v-else style="display:inline-block;width:16px"></span>
                    {{ col.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>
      <div v-if="!loading && matchedProjects.length === 0" class="empty-state">
        <el-empty v-if="isFiltered" description="没有匹配的项目，请调整筛选条件">
          <el-button size="small" @click="onReset">重置筛选</el-button>
        </el-empty>
        <el-empty v-else description="暂无项目数据">
          <el-button size="small" type="primary" @click="goToCreate">创建第一个项目</el-button>
        </el-empty>
      </div>
      <div v-else-if="error" class="error-state">
        <el-alert type="error" :closable="false" show-icon>
          <template #title>
            <span>加载失败：{{ error }}</span>
          </template>
          <template #default>
            <el-button type="primary" size="small" @click="retryLoad">重试</el-button>
          </template>
        </el-alert>
      </div>
      <div v-else class="table-wrapper">
        <el-table :data="filteredProjects" stripe @sort-change="handleSortChange" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="44" fixed="left" />
          <el-table-column type="index" label="序号" width="50" align="center" fixed="left" />
          <el-table-column label="项目名称" min-width="180" fixed="left">
            <template #default="{ row }">
              <span class="project-name-link" @click="goToDetail(row.id)">{{ row.name || row.projectName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="sourceModule" label="来源平台" width="110" align="center" v-if="columnVisible.sourceModule">
            <template #default="{ row }">
              <el-tag v-if="row.sourceModule" size="small" :type="sourceTagType(row.sourceModule)">{{ sourceText(row.sourceModule) }}</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="ownerUnit" label="招标主体" min-width="150">
            <template #default="{ row }">{{ row.ownerUnit || '-' }}</template>
          </el-table-column>
          <el-table-column prop="shortlistedCount" width="100" align="center" v-if="columnVisible.shortlistedCount" class-name="multi-line-header">
            <template #header><span class="header-two-line">计划入围<br>供应商数量</span></template>
            <template #default="{ row }">{{ row.shortlistedCount ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" prop="createdAt" width="130" sortable="custom" v-if="columnVisible.createTime">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="开标时间" prop="bidOpenTime" width="130" sortable="custom" v-if="columnVisible.bidOpenTime">
            <template #default="{ row }">{{ formatDate(row.bidOpenTime) }}</template>
          </el-table-column>
          <el-table-column prop="bidMonth" label="投标月份" width="110" v-if="columnVisible.bidMonth">
            <template #default="{ row }">{{ row.bidMonth || '-' }}</template>
          </el-table-column>
          <el-table-column label="项目类型" width="110" v-if="columnVisible.projectType">
            <template #default="{ row }"><el-tag size="small">{{ row.projectType || '-' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="客户营收（亿）" prop="revenue" width="150" sortable="custom" v-if="columnVisible.revenue">
            <template #default="{ row }">{{ row.revenue != null ? Number(row.revenue).toFixed(2) : '-' }}</template>
          </el-table-column>
          <el-table-column label="客户类型" width="140" v-if="columnVisible.customerType" class-name="wrap-cell">
            <template #default="{ row }"><div class="customer-type-cell">{{ row.customerType || '-' }}</div></template>
          </el-table-column>
          <el-table-column label="优先级" width="85" v-if="columnVisible.priority">
            <template #default="{ row }">
              <el-tag size="small" :type="priorityTag(row.priority)">{{ priorityLabel(row.priority) || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="总部所在地" width="110" v-if="columnVisible.region">
            <template #default="{ row }">{{ row.region || '-' }}</template>
          </el-table-column>
          <el-table-column prop="projectLeaderName" label="项目负责人" width="110">
            <template #default="{ row }">{{ row.projectLeaderName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="leaderDepartment" width="120" align="center" v-if="columnVisible.leaderDepartment" class-name="multi-line-header">
            <template #header><span class="header-two-line">项目负责人<br>部门</span></template>
            <template #default="{ row }">{{ row.leaderDepartment || '-' }}</template>
          </el-table-column>
          <el-table-column prop="biddingLeaderName" label="投标负责人" width="110" v-if="columnVisible.biddingLeaderName">
            <template #default="{ row }">{{ row.biddingLeaderName || '-' }}</template>
          </el-table-column>
          <el-table-column label="项目状态" width="95">
            <template #default="{ row }">
              <el-tag :type="getProjectStatusType(row.bidStatus)" size="small">{{ getProjectStatusText(row.bidStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="项目阶段" width="95" v-if="columnVisible.stage">
            <template #default="{ row }">{{ stageText(row.stage) }}</template>
          </el-table-column>
          <el-table-column label="评标状态" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.evaluationSubStage" :type="evalSubStageTag(row.evaluationSubStage)" size="small">{{ evalSubStageText(row.evaluationSubStage) }}</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="biddingPlatform" label="投标平台" min-width="130" v-if="columnVisible.biddingPlatform">
            <template #default="{ row }">{{ row.biddingPlatform || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <el-pagination
        v-if="matchedProjects.length > 0"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        class="pagination"
      />
    </el-card>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { Download, ArrowDown, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ProjectSearchCard from './components/ProjectSearchCard.vue'
import { getProjectStatusText, getProjectStatusType } from './project-utils.js'
import { formatDate, priorityTag, priorityLabel, stageText, sourceText } from './utils/projectListFormatters.js'
import { evalSubStageText, evalSubStageTag } from './utils/evalSubStageUtils.js'
import { useProjectSearch } from './composables/useProjectSearch.js'
import { useProjectFilter } from './composables/useProjectFilter.js'
import { useUserStore } from '@/stores/user'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'

const router = useRouter()
const projectStore = useProjectStore()
const userStore = useUserStore()

const selectedProjects = ref([])
function handleSelectionChange(rows) { selectedProjects.value = rows }

const {
  searchForm, userOptions, userLoading, searchUsers, isFiltered,
  projectTypeOptions, customerTypeOptions,
  sourceOptions, statusOptions, stageOptions, priorityOptions,
  generateBidMonthOptions, handleReset: clearFilters, chinaRegionOptions,
} = useProjectSearch()

const { loading, error, matchedProjects, filteredProjects, pagination, handleSizeChange, handlePageChange, resetPage, handleSortChange } =
  useProjectFilter(searchForm)

// --- useProjectExport (内联：唯一引用 + ≤80行) ---
const exporting = ref(false)

const handleExport = async () => {
  exporting.value = true
  try {
    const params = {}
    if (searchForm.value.name) params.name = searchForm.value.name
    if (searchForm.value.ownerUnit) params.ownerUnit = searchForm.value.ownerUnit
    if (searchForm.value.projectType) params.projectType = searchForm.value.projectType
    if (searchForm.value.customerType) params.customerType = searchForm.value.customerType
    if (searchForm.value.priority) params.priority = searchForm.value.priority
    if (searchForm.value.sourceModule) params.sourceModule = searchForm.value.sourceModule
    if (searchForm.value.bidStatus) params.bidStatus = searchForm.value.bidStatus
    if (searchForm.value.stage) params.stage = searchForm.value.stage
    if (searchForm.value.projectLeaderName) params.projectLeaderName = searchForm.value.projectLeaderName
    if (searchForm.value.biddingLeaderName) params.biddingLeaderName = searchForm.value.biddingLeaderName
    if (searchForm.value.leaderDepartment) params.leaderDepartment = searchForm.value.leaderDepartment
    if (searchForm.value.region) params.region = searchForm.value.region
    if (searchForm.value.biddingPlatform) params.biddingPlatform = searchForm.value.biddingPlatform
    if (searchForm.value.bidMonth) params.bidMonth = searchForm.value.bidMonth
    if (searchForm.value.shortlistedCountMin != null) params.shortlistedCountMin = searchForm.value.shortlistedCountMin
    if (searchForm.value.shortlistedCountMax != null) params.shortlistedCountMax = searchForm.value.shortlistedCountMax
    if (searchForm.value.revenueMin != null) params.revenueMin = searchForm.value.revenueMin
    if (searchForm.value.revenueMax != null) params.revenueMax = searchForm.value.revenueMax
    if (searchForm.value.bidOpenTimeRange?.length === 2) {
      params.bidOpenTimeStart = searchForm.value.bidOpenTimeRange[0]
      params.bidOpenTimeEnd = searchForm.value.bidOpenTimeRange[1]
    }
    if (searchForm.value.createTimeRange?.length === 2) {
      params.createTimeStart = searchForm.value.createTimeRange[0]
      params.createTimeEnd = searchForm.value.createTimeRange[1]
    }

    const resp = await projectLifecycleApi.exportList(params)
    const blob = await resp.data
    const safeTimestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
    const filename = `投标项目列表_${safeTimestamp}.xlsx`
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    if (e?.name === 'AbortError') {
      ElMessage.warning('导出超时，请减小筛选范围后重试')
    } else {
      ElMessage.error('导出失败：' + (e?.message || '未知错误'))
    }
  } finally {
    exporting.value = false
  }
}
// --- useProjectColumns (内联：唯一引用 + ≤80行) ---
const COLUMN_STORAGE_KEY = 'projectListColumnVisible'

const columnOptions = [
  { key: 'bidOpenTime', label: '开标时间' },
  { key: 'biddingPlatform', label: '投标平台' },
  { key: 'shortlistedCount', label: '计划入围供应商数量' },
  { key: 'createTime', label: '创建时间' },
  { key: 'bidMonth', label: '投标月份' },
  { key: 'projectType', label: '项目类型' },
  { key: 'customerType', label: '客户类型' },
  { key: 'priority', label: '优先级' },
  { key: 'sourceModule', label: '来源平台' },
  { key: 'stage', label: '项目阶段' },
  { key: 'revenue', label: '客户营收（亿）' },
  { key: 'region', label: '总部所在地' },
  { key: 'leaderDepartment', label: '项目负责人部门' },
  { key: 'biddingLeaderName', label: '投标负责人' },
]

const ALL_COLUMNS_VISIBLE = Object.fromEntries(columnOptions.map(c => [c.key, true]))

function loadColumnVisible() {
  try {
    const uid = userStore.currentUser?.id || 'default'
    const raw = localStorage.getItem(COLUMN_STORAGE_KEY + ':' + uid)
    if (raw) {
      const loaded = JSON.parse(raw)
      const validKeys = new Set(columnOptions.map((o) => o.key))
      Object.keys(loaded).forEach((k) => { if (!validKeys.has(k)) delete loaded[k] })
      return loaded
    }
  } catch (_) { /* ignore */ }
  return { ...ALL_COLUMNS_VISIBLE }
}

function saveColumnVisible(columnVisible) {
  try {
    const uid = userStore.currentUser?.id || 'default'
    localStorage.setItem(COLUMN_STORAGE_KEY + ':' + uid, JSON.stringify(columnVisible))
  } catch (_) { /* ignore */ }
}

const columnVisible = reactive(loadColumnVisible())

function toggleColumn(key) {
  columnVisible[key] = !columnVisible[key]
  saveColumnVisible(columnVisible)
}

const visibleOptionalCount = computed(() =>
  columnOptions.filter(c => columnVisible[c.key]).length
)

const bidMonthOptions = computed(() => generateBidMonthOptions())
const onSearch = () => { resetPage() }
const onReset = () => { clearFilters(); resetPage() }

async function loadProjects() {
  loading.value = true
  error.value = null
  try { await projectStore.getProjects() }
  catch (e) { error.value = e.message || '加载项目列表失败'; ElMessage.error('加载项目列表失败：' + (e.message || '未知错误')) }
  finally { loading.value = false }
}
function retryLoad() { loadProjects() }
function sourceTagType(source) {
  const map = {
    CRM: '',
    CRM_OPPORTUNITY: 'success',
    THIRD_PARTY: 'warning',
    EXTERNAL_PLATFORM: 'warning',
    MANUAL: 'info',
    MANUAL_SINGLE: 'info',
    BULK_IMPORT: 'info',
  }
  return map[source] || ''
}
const goToDetail = async (id) => {
  try { await router.push(`/project/${id}`) }
  catch (e) { ElMessage.error('跳转失败: ' + (e.message || '未知错误')) }
}
const goToCreate = () => router.push('/project/create')
onMounted(() => { loadProjects() })
</script>
<style scoped>
.project-list-container {
  padding: 16px;
  background: var(--bg-page);
  min-height: 100vh;
}
.table-card :deep(.el-card__header) { padding: 10px 14px; }
.table-card :deep(.el-card__body) { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; gap: 8px; align-items: center; }
.header-actions :deep(.el-button) { border-radius: 6px; font-size: 13px; }
.header-actions :deep(.el-button--small) { padding: 6px 14px; }
.card-header .title { font-size: 15px; font-weight: 600; color: var(--text-primary); }
.table-wrapper { overflow-x: auto; }
.table-wrapper :deep(.el-table) { --el-table-border-color: transparent; }
.table-wrapper :deep(.el-table th.el-table__cell) { background: #fafafa; color: var(--el-text-color-secondary); font-weight: 500; }
.table-wrapper :deep(.el-table th.el-table__cell > .cell) { white-space: nowrap; overflow: visible; text-overflow: clip; padding-right: 20px; position: relative; }
/* 排序箭头完全透明但保留可点击区域和状态切换 */
.table-wrapper :deep(.caret-wrapper) { opacity: 0; width: 20px; height: 24px; flex-shrink: 0; }
/* 已排序列：文字品牌色 + 单三角指示器 */
.table-wrapper :deep(th.ascending) { color: var(--brand-xiyu-logo); }
.table-wrapper :deep(th.descending) { color: var(--brand-xiyu-logo); }
.table-wrapper :deep(th.ascending > .cell)::after,
.table-wrapper :deep(th.descending > .cell)::after {
  content: '';
  position: absolute;
  right: 4px;
  top: 50%;
  border: 4px solid transparent;
}
.table-wrapper :deep(th.ascending > .cell)::after {
  border-bottom-color: var(--brand-xiyu-logo);
  margin-top: -6px;
}
.table-wrapper :deep(th.descending > .cell)::after {
  border-top-color: var(--brand-xiyu-logo);
  margin-top: 2px;
}
.table-wrapper :deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) { background: #fafbfc; }
.table-wrapper :deep(.el-table__body tr:hover > td.el-table__cell) { background: var(--brand-xiyu-logo-light) !important; }
.header-actions :deep(.el-dropdown-menu__item) { display: flex; align-items: center; gap: 6px; }
.col-count-badge { display: inline-flex; align-items: center; justify-content: center; min-width: 18px; height: 16px; padding: 0 5px; border-radius: 8px; background: var(--brand-xiyu-logo-light); color: var(--brand-xiyu-logo); font-size: 11px; font-weight: 500; line-height: 1; margin-left: 2px; }
.empty-state, .error-state { padding: 40px 0; text-align: center; }
.pagination { margin-top: 18px; display: flex; justify-content: flex-end; }
.pagination :deep(.el-pager li.is-active) { background: linear-gradient(135deg, var(--brand-xiyu-logo), var(--brand-xiyu-logo-hover)); color: var(--bg-card); }
.project-name-link,.project-name-link:hover { color: var(--el-color-primary); cursor: pointer; text-decoration: underline; }
.customer-type-cell { white-space: normal; word-break: break-word; line-height: 1.5; }
.multi-line-header :deep(.cell) { white-space: normal; line-height: 1.3; }
.header-two-line { display: inline-block; text-align: center; line-height: 1.35; }
.wrap-cell .cell { white-space: normal; word-break: break-word; }
</style>
