<template>
  <div class="qualification-container">
    <div class="page-header">
      <h2>资质证书</h2>
      <div class="page-actions">
        <el-button v-if="canManageQualification" type="primary" class="premium-btn" @click="formVisible=true; editData=null">
          <el-icon><Plus /></el-icon> 新增资质
        </el-button>
        <el-button v-if="canManageQualification" @click="downloadTemplate">下载导入模板</el-button>
        <el-button v-if="canManageQualification" @click="handleImportLedgerClick">
          <el-icon><Upload /></el-icon> 导入台账
        </el-button>
        <el-button v-if="canManageQualification" @click="batchUploadVisible = true">
          <el-icon><Document /></el-icon> 批量上传附件
        </el-button>
        <el-button v-if="canAdminQualificationAlert" @click="alertConfigVisible = true">告警配置</el-button>
        <el-button v-if="canAdminQualificationAlert" :loading="scanningExpiring" @click="handleScanExpiring">扫描到期</el-button>
      </div>
    </div>

    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters" size="default">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="证书名称/编号/机构/持有人" clearable style="width:200px" />
        </el-form-item>
        <el-form-item label="认证机构">
          <el-input v-model="filters.issuer" placeholder="模糊搜索" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker v-model="filters.expiryRange" type="daterange" range-separator="—" start-placeholder="起" end-placeholder="止" value-format="YYYY-MM-DD" style="width:220px" />
        </el-form-item>
        <el-form-item label="证书状态">
          <el-select v-model="filters.statuses" multiple collapse-tags placeholder="全部" style="width:200px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="filters.level" placeholder="全部" clearable style="width:160px">
            <el-option v-for="lv in levelOptions" :key="lv" :label="lv" :value="lv" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="fetchQualifications">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card class="data-card" shadow="never">
      <div v-if="hasSelection" class="batch-toolbar">
        <el-button type="success" size="small" @click="handleBatchExport">
          <el-icon><Download /></el-icon> 导出台账
        </el-button>
        <el-button type="success" size="small" @click="handleBatchDownload">
          <el-icon><Download /></el-icon> 批量下载附件
        </el-button>
        <span class="batch-info">已选 {{ selectedCount }} 项</span>
      </div>
      <el-upload v-show="false" ref="importUploadRef" action="" :auto-upload="false" :on-change="handleImportChange" accept=".xlsx,.xls">
        <template #trigger><span ref="importTriggerRef" /></template>
      </el-upload>
      <el-table ref="tableRef" :data="qualifications" v-loading="loading" style="width:100%" @row-click="handleRowClick" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" fixed="left" />
        <el-table-column type="index" label="序号" width="110" align="center" fixed="left" />
        <el-table-column prop="name" label="证书名称" min-width="180" fixed="left" show-overflow-tooltip>
          <template #default="scope"><span class="cert-name">{{ scope.row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="level" label="等级" width="80" align="center" />
        <el-table-column prop="issuer" label="认证机构" min-width="150" show-overflow-tooltip />
        <el-table-column prop="certificateNo" label="证书编号" width="150" show-overflow-tooltip />
        <el-table-column prop="issueDate" label="发证日期" width="120" />
        <el-table-column prop="expiryDate" label="证书有效期" width="130">
          <template #default="scope"><el-tag :type="getStatusTagType(scope.row)">{{ scope.row.expiryDate || '—' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row)" :class="{ 'retired-tag': (scope.row.status || '').toLowerCase() === 'retired' }">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="scope">
            <el-button v-if="scope.row.fileUrl" link type="primary" size="small" @click.stop="handleDownloadFile(scope.row)">下载</el-button>
            <el-button v-if="canManageQualification && (scope.row.status || '').toLowerCase() !== 'retired'" link type="primary" size="small" @click.stop="openEdit(scope.row)">编辑</el-button>
            <el-button v-if="canManageQualification && (scope.row.status || '').toLowerCase() !== 'retired'" link type="danger" size="small" @click.stop="handleRetire(scope.row)">下架</el-button>
            <el-button v-if="canManageQualification && (scope.row.status || '').toLowerCase() === 'retired'" link type="success" size="small" @click.stop="handleRestore(scope.row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total,sizes,prev,pager,next,jumper" @size-change="fetchQualifications" @current-change="fetchQualifications" />
      </div>
      <el-empty v-if="!loading && !qualifications.length" description="暂无资质数据" :image-size="80">
        <el-button v-if="canManageQualification" type="primary" size="small" @click="formVisible=true; editData=null"><el-icon><Plus /></el-icon>新增资质</el-button>
      </el-empty>
      <el-empty v-else-if="!loading && hasFilterActive" description="未找到匹配的证书，请调整筛选条件" :image-size="80">
        <el-button size="small" @click="resetFilters">重置筛选</el-button>
      </el-empty>
    </el-card>


    <QualFormDialog v-model="formVisible" :initial-data="editData" :status="editData?.status" @saved="handleFormSaved" />
    <AlertConfigDialog v-model="alertConfigVisible" />
    <QualDetailDrawer
      v-model="detailDrawerVisible"
      :qualification="detailQualification"
      :attachments="detailAttachments"
      :can-manage="canManageQualification"
      @edit="handleDetailEdit"
      @retire="handleDetailRetire"
      @restore="handleDetailRestore"
      @download="handleDetailDownload"
      @replace="handleAttachmentReplace"
      @delete="handleAttachmentDelete"
      @upload="handleAttachmentUpload"
    />
    <AttachmentReplaceDialog
      v-model="replaceDialogVisible"
      :qualification-id="replaceQualificationId"
      :current-file-name="replaceCurrentFileName"
      @success="handleAttachmentActionSuccess"
    />

    <ImportResultDialog
      v-model="importResultVisible"
      :data="importResultData"
      @closed="fetchQualifications"
    />
    <QualBatchUploadDialog
      v-model="batchUploadVisible"
      
      @closed="fetchQualifications"
    />
    <RetireConfirmDialog
      v-model="retireDialogVisible"
      :data="retireTarget"
      @confirm="handleRetireConfirm"
      @closed="retireTarget = null"
    />

  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Document, Download } from '@element-plus/icons-vue'
import http from '@/api/client'
import { useQualificationBatch } from './components/qualification/useQualificationBatch.js'
import { useQualificationStore } from '@/stores/qualification'
import { useUserStore } from '@/stores/user.js'
import QualFormDialog from './components/qualification/QualFormDialog.vue'
import AlertConfigDialog from './components/qualification/AlertConfigDialog.vue'
import AttachmentReplaceDialog from './components/qualification/AttachmentReplaceDialog.vue'
import ImportResultDialog from './components/qualification/ImportResultDialog.vue'
import QualBatchUploadDialog from "./components/qualification/QualBatchUploadDialog.vue"
import { useQualificationPermissionMatrix, useQualificationBorrowSection } from './components/qualification/useQualificationBorrowSection.js'
import QualDetailDrawer from './components/qualification/QualDetailDrawer.vue'
import RetireConfirmDialog from './components/qualification/RetireConfirmDialog.vue'

const userStore = useUserStore()
const qualificationStore = useQualificationStore()
const {
  canManageQualification,
  canViewQualification,
  canAdminQualificationAlert
} = useQualificationPermissionMatrix(userStore)

const qualifications = ref([]); const loading = ref(false)
const page = ref(1); const pageSize = ref(15); const total = ref(0)
// filters: 初始无默认筛选，空状态显示全部
const filters = reactive({ keyword:'', issuer:'', expiryRange:null, statuses:[], level:'' })
const statusOptions = [{ label:'在库', value:'VALID' },{ label:'即将到期', value:'EXPIRING' },{ label:'已过期', value:'EXPIRED' },{ label:'已下架', value:'RETIRED' }]
const STATUS_LABELS ={ in_stock:'在库', valid:'在库', expiring:'即将到期', expired:'已过期', retired:'已下架' }

const hasFilterActive = computed(() => filters.keyword || filters.issuer || filters.expiryRange || filters.statuses.length || filters.level)
const formVisible = ref(false); const editData = ref(null)
const batchUploadVisible = ref(false)
const retireDialogVisible = ref(false)
const retireTarget = ref(null)
const {
  alertConfigVisible,
  scanningExpiring,
  handleScanExpiring
} = useQualificationBorrowSection({
  httpClient: http
})

const fetchQualifications = async () => {
  loading.value = true
  try {
    const q = new URLSearchParams()
    if (filters.keyword) q.set('keyword', filters.keyword)
    if (filters.issuer) q.set('issuer', filters.issuer)
    if (filters.expiryRange) { q.set('expiringFrom', filters.expiryRange[0]); q.set('expiringTo', filters.expiryRange[1]) }
    if (filters.statuses.length) filters.statuses.forEach(s => q.append('status', s))
    if (filters.level) q.set('level', filters.level)
    // CO-155 fix: 前端 page 从 1 开始 → 后端从 0 开始
    q.set('page', page.value - 1)
    q.set('size', pageSize.value)
    const body = await http.get(`/api/knowledge/qualifications?${q.toString()}`)
    if (body?.code === 200) {
      // CO-155 fix: 后端现在返回 Page<DTO>，有 content/totalElements 字段
      const data = body.data
      if (data && Array.isArray(data.content)) {
        qualifications.value = data.content
        total.value = data.totalElements ?? data.content.length
      } else {
        // 兜底：老接口返回 List<DTO>
        qualifications.value = Array.isArray(data) ? data : []
        total.value = qualifications.value.length
      }
    }
  } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

const levelOptions = computed(() => {
  const levels = new Set()
  qualifications.value.forEach((item) => { if (item.level) levels.add(item.level) })
  return Array.from(levels).sort()
})

const {
  tableRef,
  hasSelection,
  selectedCount,
  handleSelectionChange,
  importResultVisible,
  importResultData,
  importUploadRef,
  importTriggerRef,
  handleImportLedgerClick,
  handleImportChange,
  handleImportResultClosed,
  handleBatchExport,
  handleBatchDownload
} = useQualificationBatch({ fetchQualifications })

const resetFilters = () => { Object.assign(filters, { keyword:'', issuer:'', expiryRange:null, statuses:[], level:'' }); page.value = 1; fetchQualifications() }
const getStatusTagType = (row) => { const s = (row.status || '').toLowerCase(); if (s === 'in_stock' || s === 'valid') return 'success'; if (s === 'expiring') return 'warning'; if (s === 'expired') return 'danger'; return 'info' }
const statusLabel = (s) => STATUS_LABELS[(s || '').toLowerCase()] || s || '—'
const openEdit = (row) => { editData.value = row; formVisible.value = true }
const handleRetire = (row) => {
  retireTarget.value = row
  retireDialogVisible.value = true
}
const handleRetireConfirm = async ({ id, reason }) => {
  try {
    await http.post(`/api/knowledge/qualifications/${id}/retire`, { reason })
    ElMessage.success('已下架')
    fetchQualifications()
  } catch {
    ElMessage.error('下架失败')
  }
}
const handleRestore = async (row) => {
  try { await ElMessageBox.confirm('确认恢复该资质证书？', '恢复在库')
    await http.post(`/api/knowledge/qualifications/${row.id}/restore`)
    ElMessage.success('已恢复'); fetchQualifications()
  } catch { /* cancelled */ }
}

// 4.1.3.6 资质详情抽屉
const detailDrawerVisible = ref(false)
const detailQualification = ref(null)
const detailAttachments = ref([])
const openDetailDrawer = (row) => {
  detailQualification.value = row
  detailAttachments.value = Array.isArray(row?.attachments) ? row.attachments : []
  detailDrawerVisible.value = true
}
const handleDetailEdit = (row) => { detailDrawerVisible.value = false; openEdit(row) }
const handleDetailRetire = (row) => { detailDrawerVisible.value = false; handleRetire(row) }
const handleDetailRestore = (row) => { detailDrawerVisible.value = false; handleRestore(row) }
const handleDetailDownload = (att) => { ElMessage.success(`已下载：${att.fileName || '附件'}`) }
const handleRowClick = (row) => { if (row) openDetailDrawer(row) }

// 4.2.1.3 编辑资质 - 附件管理
const replaceDialogVisible = ref(false)
const replaceQualificationId = ref(null)
const replaceCurrentFileName = ref('')

const handleFormSaved = () => {
  // CO-155 fix: 保存后重置 page=1，确保新数据（按 id desc 排第 1 页）立刻可见
  page.value = 1
  fetchQualifications()
  if (detailDrawerVisible.value && detailQualification.value) {
    const updated = qualifications.value.find(q => q.id === detailQualification.value.id)
    if (updated) {
      detailQualification.value = updated
      detailAttachments.value = Array.isArray(updated.attachments) ? updated.attachments : []
    }
  }
}

const handleAttachmentActionSuccess = () => {
  fetchQualifications()
  const id = detailQualification.value?.id
  if (id) {
    const updated = qualifications.value.find(q => q.id === id)
    if (updated) {
      detailQualification.value = updated
      detailAttachments.value = Array.isArray(updated.attachments) ? updated.attachments : []
    }
  }
}

const handleAttachmentReplace = (att) => {
  replaceQualificationId.value = detailQualification.value?.id
  replaceCurrentFileName.value = att?.fileName || att?.name || ''
  replaceDialogVisible.value = true
}

const handleAttachmentDelete = async (att) => {
  const fileName = att?.fileName || att?.name || '该附件'
  try {
    await ElMessageBox.confirm(
      `确认删除附件 ${fileName}？\n\n删除后证书将处于"无附件"状态，可能影响后续投标资质佐证。建议尽快上传新附件。\n\n该操作将被记录在操作日志中。`,
      '删除附件',
      { confirmButtonText: '确认删除', confirmButtonClass: 'el-button--danger', type: 'warning' }
    )
    const id = detailQualification.value?.id
    if (!id) return
    await http.put(`/api/knowledge/qualifications/${id}`, { fileUrl: null })
    ElMessage.success('附件已删除')
    handleAttachmentActionSuccess()
  } catch { /* cancelled */ }
}

const handleAttachmentUpload = () => {
  replaceQualificationId.value = detailQualification.value?.id
  replaceCurrentFileName.value = ''
  replaceDialogVisible.value = true
}

const handleDownloadFile = async (row) => {
  try {
    const res = await http.get(row.fileUrl, { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = row.name || '资质附件'
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  } catch { ElMessage.error('下载失败') }
}

onMounted(async () => {
  await fetchQualifications()
})

const downloadTemplate = async () => {
  try {
    const resp = await http.get('/api/knowledge/qualifications/template', { responseType: 'blob' })
    const url = URL.createObjectURL(resp.data)
    const a = document.createElement('a')
    a.href = url
    a.download = '资质证书导入模板.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch { ElMessage.warning('模板下载失败，请稍后重试') }
}
</script>

<style scoped lang="scss">
.qualification-container {
  background: var(--bg-page);
  min-height: 100vh;
}
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; h2 { font-weight:600; color:#1f2937; margin:0 } }
.page-actions { display: flex; gap: 12px; flex-wrap: wrap; }
.premium-btn { background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%); border: none; box-shadow: 0 4px 6px -1px rgba(99,102,241,0.4); transition: all 0.3s ease; &:hover { transform: translateY(-2px); box-shadow: 0 6px 8px -1px rgba(99,102,241,0.5); } }
.filter-card,.data-card { border-radius:8px; border:1px solid var(--el-border-color-lighter); box-shadow:0 2px 8px rgba(0,0,0,.05); margin-bottom:12px }
.pagination-wrap { display:flex; justify-content:flex-end; margin-top:16px }
.cert-name { font-weight:500; color:#1f2937 }
.text-muted { color:#9ca3af }
.retired-tag { text-decoration:line-through; opacity:0.6 }
.batch-toolbar { display:flex; align-items:center; gap:12px; margin-bottom:12px; padding:8px 12px; background:var(--el-fill-color-light); border-radius:6px; border:1px solid var(--el-border-color-lighter) }
.batch-info { margin-left:auto; color:var(--el-text-color-secondary); font-size:13px }
</style>
