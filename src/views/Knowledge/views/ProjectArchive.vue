<template>
  <div class="project-archive-container">
    <ArchiveStatsCards
      :total-archives="stats.totalArchives"
      :closed-projects="stats.closedProjects"
      :case-count="stats.caseCount"
      :reuse-count="stats.reuseCount"
    />

    <ArchiveStatusTabs v-model="activeStatusTab" @change="handleStatusTabChange" />

    <el-card class="filter-card">
      <template #header>
        <div class="card-header-title"><el-icon><Files /></el-icon><span>项目档案台账</span></div>
      </template>
      <el-form :inline="true" :model="filters" class="search-form">
        <el-form-item label="项目名称">
          <el-input v-model="filters.projectName" placeholder="请输入项目名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="文档分类">
          <el-select v-model="filters.categories" placeholder="选择分类" multiple clearable collapse-tags collapse-tags-tooltip style="width: 200px">
            <el-option label="招标文件" value="TENDER" />
            <el-option label="标书文件" value="BID" />
            <el-option label="开标一览表" value="OPEN_LIST" />
            <el-option label="中标通知书" value="WIN_NOTICE" />
            <el-option label="保证金银行回单" value="DEPOSIT_RECEIPT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目负责人">
          <el-select v-model="filters.projectManager" placeholder="选择负责人" clearable filterable style="width: 140px">
            <el-option v-for="m in projectManagerOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="投标负责人">
          <el-select v-model="filters.bidManager" placeholder="选择负责人" clearable filterable style="width: 140px">
            <el-option v-for="m in bidManagerOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传时间">
          <el-date-picker v-model="filters.uploadDates" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="结项时间">
          <el-date-picker v-model="filters.endDates" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="项目状态">
          <el-select v-model="filters.projectStatus" placeholder="选择状态" multiple clearable collapse-tags collapse-tags-tooltip style="width: 200px">
            <el-option label="待立项" value="PENDING_INITIATION" />
            <el-option label="已立项" value="INITIATED" />
            <el-option label="投标中" value="BIDDING" />
            <el-option label="评标中" value="EVALUATING" />
            <el-option label="已中标" value="WON" />
            <el-option label="未中标" value="LOST" />
            <el-option label="已流标" value="FAILED" />
            <el-option label="已放弃" value="ABANDONED" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类型">
          <el-select v-model="filters.projectType" placeholder="选择类型" multiple clearable collapse-tags collapse-tags-tooltip style="width: 180px">
            <el-option label="办公" value="OFFICE" />
            <el-option label="综合" value="COMPREHENSIVE" />
            <el-option label="集采" value="CENTRALIZED" />
            <el-option label="工业品" value="INDUSTRIAL" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon class="mr-1"><Search /></el-icon>查询</el-button>
          <el-button @click="handleReset"><el-icon class="mr-1"><Refresh /></el-icon>重置</el-button>
          <el-button type="success" @click="handleExportExcel">📊 导出台账</el-button>
          <el-button type="warning" @click="handleExportZip">📦 导出文件包</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" v-loading="loading">
      <el-table :data="tableData" style="width: 100%" border stripe highlight-current-row @row-click="handleRowClick" class="custom-table">
        <el-table-column prop="projectName" label="项目名称" min-width="250" show-overflow-tooltip />
        <el-table-column prop="projectType" label="项目类型" width="120" align="center">
          <template #default="{ row }"><el-tag>{{ getProjectTypeLabel(row.projectType) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="projectStatus" label="项目状态" width="120" align="center">
          <template #default="{ row }"><el-tag :type="getStatusTagType(row.projectStatus)">{{ getStatusLabel(row.projectStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="bidResult" label="中标结果" width="100" align="center">
          <template #default="{ row }"><el-tag :type="getBidResultTagType(row.bidResult)">{{ getBidResultLabel(row.bidResult) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="归档文件数" width="160" align="center">
          <template #default="{ row }">
            <FileCategoryPopover :category-details="row.fileCategoryDetails" :file-count="row.fileCount" @click.stop>
              <el-tag type="info" class="clickable-tag">
                <el-icon><Files /></el-icon>{{ row.fileCount }} 份
              </el-tag>
            </FileCategoryPopover>
          </template>
        </el-table-column>
        <el-table-column prop="lastUploadedAt" label="归档时间" width="160" align="center">
          <template #default="{ row }">{{ formatDate(row.lastUploadedAt) }}</template>
        </el-table-column>
        <el-table-column prop="projectManager" label="项目负责人" width="120" align="center">
          <template #default="{ row }">{{ row.projectManager || '-' }}</template>
        </el-table-column>
        <el-table-column prop="bidManager" label="投标负责人" width="120" align="center">
          <template #default="{ row }">{{ row.bidManager || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleRowClick(row)">查看</el-button>
            <el-button type="success" link @click.stop="handleDownloadArchive(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-container">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper" :total="totalElements" @current-change="loadData" @size-change="handleSizeChange" />
      </div>
    </el-card>

    <ArchiveDetailDrawer ref="drawerRef" :archive="selectedArchive" v-model:visible="drawerVisible" @preview-file="handlePreviewFile" @download-file="handleDownloadFile" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Files, Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import httpClient from '@/api/client.js'
import FileCategoryPopover from '../components/FileCategoryPopover.vue'
import ArchiveStatsCards from './components/ArchiveStatsCards.vue'
import ArchiveStatusTabs from './components/ArchiveStatusTabs.vue'
import ArchiveDetailDrawer from './components/ArchiveDetailDrawer.vue'
import { formatDate, getStatusLabel, getStatusTagType } from './archiveLabels.js'

const activeStatusTab = ref('ALL')

const filters = reactive({
  projectName: '',
  categories: [],
  projectManager: '',
  bidManager: '',
  uploadDates: null,
  endDates: null,
  projectStatus: [],
  projectType: []
})

const stats = reactive({ totalArchives: 0, closedProjects: 0, caseCount: 0, reuseCount: 0 })
const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const pageSize = ref(10)
const drawerRef = ref(null)
const drawerVisible = ref(false)
const selectedArchive = ref(null)
const projectManagerOptions = ref([])
const bidManagerOptions = ref([])

const getProjectTypeLabel = (type) => {
  const map = { OFFICE: '办公', COMPREHENSIVE: '综合', CENTRALIZED: '集采', INDUSTRIAL: '工业品', OTHER: '其他' }
  return map[type] || type || '-'
}

const getBidResultLabel = (result) => {
  const map = { AWARDED: '中标', WON: '中标', LOST: '未中标', ABANDONED: '流标', IN_PROGRESS: '进行中', OTHER: '其他' }
  return map[result] || result || '-'
}

const getBidResultTagType = (result) => {
  if (result === 'AWARDED' || result === 'WON') return 'success'
  if (result === 'LOST') return 'danger'
  if (result === 'ABANDONED') return 'warning'
  return 'info'
}

const buildQueryParams = () => {
  const params = {
    page: page.value - 1, size: pageSize.value,
    projectName: filters.projectName.trim() || null,
    documentCategories: filters.categories.length ? filters.categories : null,
    uploadTimeStart: filters.uploadDates?.[0] || null,
    uploadTimeEnd: filters.uploadDates?.[1] || null,
    closeTimeStart: filters.endDates?.[0] || null,
    closeTimeEnd: filters.endDates?.[1] || null,
    projectStatus: filters.projectStatus.length ? filters.projectStatus : null,
    projectType: filters.projectType.length ? filters.projectType : null,
    projectManager: filters.projectManager || null,
    bidManager: filters.bidManager || null
  }
  Object.keys(params).forEach(k => params[k] === null && delete params[k])
  return params
}

const loadStats = async () => {
  try {
    const res = await httpClient.get('/api/archive/stats')
    Object.assign(stats, { totalArchives: res.totalArchives || 0, closedProjects: res.closedProjects || 0, caseCount: res.caseCount || 0, reuseCount: res.reuseCount || 0 })
    if (Array.isArray(res.projectManagers)) projectManagerOptions.value = res.projectManagers
    if (Array.isArray(res.bidManagers)) bidManagerOptions.value = res.bidManagers
  } catch (e) { console.error('Failed to load stats:', e) }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await httpClient.get('/api/archive', { params: buildQueryParams() })
    tableData.value = res.content || []
    totalElements.value = res.totalElements || 0
  } catch (e) { ElMessage.error('加载项目档案失败'); console.error(e) }
  finally { loading.value = false }
}

const handleStatusTabChange = (status) => {
  filters.projectStatus = status === 'ALL' ? [] : [status]
  page.value = 1
  loadData()
}

const handleSearch = () => { page.value = 1; loadData() }
const handleReset = () => {
  activeStatusTab.value = 'ALL'
  Object.assign(filters, { projectName: '', categories: [], projectManager: '', bidManager: '', uploadDates: null, endDates: null, projectStatus: [], projectType: [] })
  page.value = 1; loadData()
}
const handleSizeChange = () => { page.value = 1; loadData() }
const handleRowClick = (row) => { selectedArchive.value = row; drawerVisible.value = true }

const buildExportParams = () => {
  const params = {
    projectName: filters.projectName.trim() || null,
    documentCategories: filters.categories.length ? filters.categories : null,
    uploadTimeStart: filters.uploadDates?.[0] || null,
    uploadTimeEnd: filters.uploadDates?.[1] || null,
    closeTimeStart: filters.endDates?.[0] || null,
    closeTimeEnd: filters.endDates?.[1] || null,
    projectStatus: filters.projectStatus.length ? filters.projectStatus : null,
    projectType: filters.projectType.length ? filters.projectType : null,
    projectManager: filters.projectManager || null,
    bidManager: filters.bidManager || null
  }
  Object.keys(params).forEach(k => params[k] === null && delete params[k])
  return params
}

const downloadBlob = (blob, filename, mimeType) => {
  const link = document.createElement('a')
  link.href = window.URL.createObjectURL(new Blob([blob], { type: mimeType }))
  link.download = filename; link.click(); window.URL.revokeObjectURL(link.href)
}

const handleDownloadArchive = async (row) => {
  try {
    const params = row ? { ...buildExportParams(), archiveId: row.archiveId } : buildExportParams()
    const blob = (await httpClient.post('/api/archive/export-zip', params, { responseType: 'blob' })).data
    downloadBlob(blob, `方案管理-项目档案文件包-${new Date().toISOString().replace(/[-:T]/g, '').slice(0, 12)}.zip`, 'application/zip')
    ElMessage.success('导出文件包成功')
  } catch { ElMessage.error('导出文件包失败') }
}

const handleExportExcel = async () => {
  try {
    const blob = (await httpClient.post('/api/archive/export-excel', buildExportParams(), { responseType: 'blob' })).data
    downloadBlob(blob, `方案管理-项目档案台账-${new Date().toISOString().replace(/[-:T]/g, '').slice(0, 12)}.xlsx`, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
    ElMessage.success('导出台账成功')
  } catch { ElMessage.error('导出台账失败') }
}

const handleExportZip = async () => { await handleDownloadArchive(null) }
const handlePreviewFile = async (file) => {
  const name = (file.fileName || '').toLowerCase()
  if (!name.endsWith('.pdf')) { ElMessage.info('该格式不支持在线预览，请下载查看'); return }
  try {
    const res = await httpClient.get(`/api/archive/files/${file.fileId}/preview`, { responseType: 'blob' })
    window.open(window.URL.createObjectURL(res), '_blank')
    drawerRef.value?.fetchDetail()
  } catch { ElMessage.warning('文件预览失败') }
}
const handleDownloadFile = async (file) => {
  try {
    const res = await httpClient.get(`/api/archive/files/${file.fileId}/download`, { responseType: 'blob' })
    downloadBlob(res, file.fileName || '下载文件', res.type || 'application/octet-stream')
    drawerRef.value?.fetchDetail()
  } catch { ElMessage.warning('文件下载失败') }
}

onMounted(() => { loadStats(); loadData() })
</script>

<style scoped lang="scss">
.project-archive-container { padding: 16px; display: flex; flex-direction: column; gap: 16px; }
.filter-card, .table-card { border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05); border: 1px solid var(--el-border-color-lighter); }
.card-header-title { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; color: var(--el-text-color-primary); }
.search-form { margin-bottom: -18px; }
.custom-table { border-radius: 6px; overflow: hidden; --el-table-header-bg-color: var(--el-fill-color-light); }
.clickable-tag { cursor: pointer; display: inline-flex; align-items: center; gap: 4px; transition: all 0.2s ease; &:hover { transform: scale(1.05); background-color: var(--el-color-info-light-7); } }
.pagination-container { display: flex; justify-content: flex-end; margin-top: 16px; }
.mr-1 { margin-right: 4px; }
</style>
