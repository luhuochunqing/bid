<template>
  <div class="qualification-container">
    <div class="page-header">
      <h2>资质证书</h2>
      <div class="page-actions">
        <el-button v-if="canManageQualification" type="primary" class="premium-btn" @click="formVisible=true; editData=null">
          <el-icon><Plus /></el-icon> 新增资质
        </el-button>
        <el-button v-if="canManageQualification" @click="window.open('/api/knowledge/qualifications/template')">下载导入模板</el-button>
        <el-button v-if="canViewQualification" @click="window.open('/api/knowledge/qualifications/export')">导出台账</el-button>
        <el-button v-if="canAdminQualificationAlert" @click="alertConfigVisible = true">告警配置</el-button>
        <el-button v-if="canAdminQualificationAlert" :loading="scanningExpiring" @click="handleScanExpiring">扫描到期</el-button>
      </div>
    </div>

    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters" size="default">
        <el-form-item label="证书名称">
          <el-input v-model="filters.keyword" placeholder="模糊搜索" clearable style="width:160px" />
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
        <el-button type="primary" size="small" @click="handleImportLedgerClick">
          <el-icon><Upload /></el-icon> 导入台账
        </el-button>
        <el-button type="primary" size="small" @click="handleBatchUploadClick">
          <el-icon><Document /></el-icon> 批量上传附件
        </el-button>
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
      <el-upload v-show="false" ref="batchAttachUploadRef" action="" :auto-upload="false" multiple :on-change="handleBatchAttachChange">
        <template #trigger><span ref="batchAttachTriggerRef" /></template>
      </el-upload>
      <el-table ref="tableRef" :data="qualifications" v-loading="loading" style="width:100%" @row-click="handleRowClick" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="name" label="证书名称" min-width="180" fixed="left" show-overflow-tooltip>
          <template #default="scope"><span class="cert-name">{{ scope.row.name }}</span></template>
        </el-table-column>
        <el-table-column label="证书附件" width="100" align="center">
          <template #default="scope">
            <el-button v-if="scope.row.fileUrl && canViewQualification" link type="primary" size="small" @click.stop="handleDownload(scope.row)">下载</el-button>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="等级" width="80" align="center" />
        <el-table-column prop="issuer" label="认证机构" min-width="150" show-overflow-tooltip />
        <el-table-column prop="certificateNo" label="证书编号" width="150" show-overflow-tooltip />
        <el-table-column prop="issueDate" label="发证日期" width="110" />
        <el-table-column prop="expiryDate" label="证书有效期" width="120">
          <template #default="scope"><el-tag :type="getStatusTagType(scope.row)">{{ scope.row.expiryDate || '—' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="借阅状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="getBorrowStatusTagType(scope.row.currentBorrowStatus)">{{ getBorrowStatusLabel(scope.row.currentBorrowStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row)" :class="{ 'retired-tag': scope.row.status === 'RETIRED' }">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="scope">
            <el-button v-if="scope.row.fileUrl" link type="primary" size="small" @click.stop="handleDownload(scope.row)">下载</el-button>
            <el-button v-if="canViewQualification" link type="warning" size="small" @click.stop="openBorrow(scope.row)">借阅</el-button>
            <el-button v-if="canViewQualification" link type="info" size="small" @click.stop="openBorrowHistory(scope.row)">记录</el-button>
            <el-button v-if="canManageQualification && scope.row.status !== 'RETIRED'" link type="primary" size="small" @click.stop="openEdit(scope.row)">编辑</el-button>
            <el-button v-if="canManageQualification && scope.row.status !== 'RETIRED'" link type="danger" size="small" @click.stop="handleRetire(scope.row)">下架</el-button>
            <el-button v-if="canManageQualification && scope.row.status === 'RETIRED'" link type="success" size="small" @click.stop="handleRestore(scope.row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total,sizes,prev,pager,next,jumper" @size-change="fetchQualifications" @current-change="fetchQualifications" />
      </div>
      <el-empty v-if="!loading && !qualifications.length" :description="emptyDescription">
        <template #description>
          <span>{{ emptyDescription }}</span>
        </template>
      </el-empty>
      <el-empty v-else-if="!loading && hasFilterActive" description="未找到匹配的证书，请调整筛选条件" />
    </el-card>

    <el-card v-if="canViewQualification" class="borrow-history-wrap" shadow="never">
      <QualificationBorrowHistoryCard
        :loading="borrowLoading"
        :records="borrowRecords"
        :feature-placeholder="borrowFeaturePlaceholder"
        @create="openBorrowFromHistory"
        @return="handleReturnBorrow"
      />
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
    <QualificationBorrowDialog
      v-model="borrowApplyDialogVisible"
      :form="borrowForm"
      :schema="borrowFormSchema"
      :qualification="currentBorrowQualification"
      :feature-placeholder="borrowFeaturePlaceholder"
      @confirm="submitBorrowApplication"
    />

    <ImportResultDialog
      v-model="importResultVisible"
      :data="importResultData"
      @closed="fetchQualifications"
    />
    <BatchAttachResultDialog
      v-model="attachResultVisible"
      :data="attachResultData"
      @closed="fetchQualifications"
    />
    <RetireConfirmDialog
      v-model="retireDialogVisible"
      :data="retireTarget"
      @confirm="handleRetireConfirm"
      @closed="retireTarget = null"
    />

    <el-dialog v-model="borrowDialogVisible" title="安全审计" width="450px">
      <el-alert title="该资质证书为敏感机密件" type="warning" description="系统将校验借阅权限。操作将被审计。" show-icon :closable="false" class="mb-4" />
      <el-form><el-form-item label="关联项目ID"><el-input v-model="currentProjectId" placeholder="请输入投标项目ID" /></el-form-item></el-form>
      <template #footer>
        <el-button @click="borrowDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="checkingBorrow" @click="confirmBorrowCheck">验证并查看</el-button>
      </template>
    </el-dialog>

    <!-- 导入结果报告 -->
    <ImportResultDialog
      v-model="importResultVisible"
      :data="importResultData"
      @closed="handleImportResultClosed"
    />

    <!-- 批量关联附件结果 -->
    <BatchAttachResultDialog
      v-model="attachResultVisible"
      :data="attachResultData"
      @closed="handleAttachResultClosed"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Document, Download } from '@element-plus/icons-vue'
import http from '@/api/client'
import { useQualificationBatch } from './components/qualification/useQualificationBatch.js'
import { getBorrowStatusLabel, borrowStatusTagTypes } from './components/qualification/qualificationMeta.js'
import { useQualificationStore } from '@/stores/qualification'
import { useUserStore } from '@/stores/user.js'
import QualFormDialog from './components/qualification/QualFormDialog.vue'
import AlertConfigDialog from './components/qualification/AlertConfigDialog.vue'
import AttachmentReplaceDialog from './components/qualification/AttachmentReplaceDialog.vue'
import QualificationBorrowDialog from './components/qualification/QualificationBorrowDialog.vue'
import QualificationBorrowHistoryCard from './components/qualification/QualificationBorrowHistoryCard.vue'
import ImportResultDialog from './components/qualification/ImportResultDialog.vue'
import BatchAttachResultDialog from './components/qualification/BatchAttachResultDialog.vue'
import {
  useQualificationBorrowSection,
  useQualificationPermissionMatrix
} from './components/qualification/useQualificationBorrowSection.js'
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
const filters = reactive({ keyword:'', issuer:'', expiryRange:null, statuses:['IN_STOCK','EXPIRING','EXPIRED'], level:'' })
const statusOptions = [{ label:'在库', value:'IN_STOCK' },{ label:'即将到期', value:'EXPIRING' },{ label:'已过期', value:'EXPIRED' },{ label:'已下架', value:'RETIRED' }]
const STATUS_LABELS = { IN_STOCK:'在库', EXPIRING:'即将到期', EXPIRED:'已过期', RETIRED:'已下架', VALID:'在库' }

const hasFilterActive = computed(() => filters.keyword || filters.issuer || filters.expiryRange || filters.statuses.length || filters.level)
const emptyDescription = computed(() => {
  if (!canViewQualification.value) return '暂无权限查看资质证书'
  return '暂无资质证书，点击右上角新增'
})

const formVisible = ref(false); const editData = ref(null)
const retireDialogVisible = ref(false)
const retireTarget = ref(null)
const {
  alertConfigVisible,
  scanningExpiring,
  borrowDialogVisible,
  currentProjectId,
  checkingBorrow,
  borrowApplyDialogVisible,
  currentBorrowQualification,
  borrowFormSchema,
  borrowForm,
  borrowRecords,
  borrowLoading,
  borrowFeaturePlaceholder,
  loadBorrowRecords,
  openBorrow,
  openBorrowFromHistory,
  openBorrowHistory,
  submitBorrowApplication,
  handleReturnBorrow,
  handleDownload,
  confirmBorrowCheck,
  handleScanExpiring
} = useQualificationBorrowSection({
  qualificationStore,
  httpClient: http,
  canViewQualification,
  qualificationsRef: qualifications
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
    q.set('page', page.value-1); q.set('size', pageSize.value)
    const body = await http.get(`/api/knowledge/qualifications?${q.toString()}`)
    if (body?.code === 200) { qualifications.value = body.data?.content || body.data || []; total.value = body.data?.totalElements || qualifications.value.length }
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
  batchAttachUploadRef,
  batchAttachTriggerRef,
  handleBatchUploadClick,
  handleBatchAttachChange,
  attachResultVisible,
  attachResultData,
  handleAttachResultClosed,
  handleBatchExport,
  handleBatchDownload
} = useQualificationBatch({ fetchQualifications })

const resetFilters = () => { Object.assign(filters, { keyword:'', issuer:'', expiryRange:null, statuses:[], level:'' }); page.value = 1; fetchQualifications() }
const getStatusTagType = (row) => { const s = row.status || ''; if (s === 'IN_STOCK' || s === 'VALID') return 'success'; if (s === 'EXPIRING') return 'warning'; if (s === 'EXPIRED') return 'danger'; return 'info' }
const getBorrowStatusTagType = (status) => borrowStatusTagTypes[status] || 'info'
const statusLabel = (s) => STATUS_LABELS[s] || s || '—'
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

onMounted(async () => {
  await fetchQualifications()
  await loadBorrowRecords()
})
</script>

<style scoped lang="scss">
.qualification-container { padding: 24px; }
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; h2 { font-weight:600; color:#1f2937; margin:0 } }
.page-actions { display: flex; gap: 12px; flex-wrap: wrap; }
.premium-btn { background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%); border: none; box-shadow: 0 4px 6px -1px rgba(99,102,241,0.4); transition: all 0.3s ease; &:hover { transform: translateY(-2px); box-shadow: 0 6px 8px -1px rgba(99,102,241,0.5); } }
.filter-card,.data-card,.borrow-history-wrap { border-radius:8px; border:1px solid var(--el-border-color-lighter); box-shadow:0 2px 8px rgba(0,0,0,.05); margin-bottom:12px }
.pagination-wrap { display:flex; justify-content:flex-end; margin-top:16px }
.cert-name { font-weight:500; color:#1f2937 }
.text-muted { color:#9ca3af }
.retired-tag { text-decoration:line-through; opacity:0.6 }
.batch-toolbar { display:flex; align-items:center; gap:12px; margin-bottom:12px; padding:8px 12px; background:var(--el-fill-color-light); border-radius:6px; border:1px solid var(--el-border-color-lighter) }
.batch-info { margin-left:auto; color:var(--el-text-color-secondary); font-size:13px }
</style>
