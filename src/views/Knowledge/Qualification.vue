<template>
  <div class="qualification-container">
    <div class="page-header">
      <h2>资质证书</h2>
      <div class="page-actions">
        <el-button v-if="canManageQualification" type="primary" class="premium-btn" @click="formVisible=true; editData=null">
          <el-icon><Plus /></el-icon> 新增资质
        </el-button>
        <el-button v-if="canManageQualification" @click="importCombinedVisible = true">
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
      @preview="handleDetailPreview"
      @download="handleDetailDownload"
      @replace="handleAttachmentReplace"
      @delete="handleAttachmentDelete"
      @upload="handleAttachmentUpload"
    />
    <AttachmentReplaceDialog
      v-model="replaceDialogVisible"
      :qualification-id="replaceQualificationId"
      :attachment-id="replaceAttachmentId"
      :current-file-name="replaceCurrentFileName"
      @success="handleAttachmentActionSuccess"
    />

    <QualImportCombinedDialog
      v-model="importCombinedVisible"
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
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Document, Download } from '@element-plus/icons-vue'
import http from '@/api/client'
import { useQualificationBatch } from './components/qualification/useQualificationBatch.js'
import { useUserStore } from '@/stores/user.js'
import QualFormDialog from './components/qualification/QualFormDialog.vue'
import AlertConfigDialog from './components/qualification/AlertConfigDialog.vue'
import AttachmentReplaceDialog from './components/qualification/AttachmentReplaceDialog.vue'
import QualImportCombinedDialog from './components/qualification/QualImportCombinedDialog.vue'
import QualBatchUploadDialog from "./components/qualification/QualBatchUploadDialog.vue"
import { useQualificationPermissionMatrix, useQualificationBorrowSection } from './components/qualification/useQualificationBorrowSection.js'
import QualDetailDrawer from './components/qualification/QualDetailDrawer.vue'
import RetireConfirmDialog from './components/qualification/RetireConfirmDialog.vue'
import { useQualificationList } from './components/qualification/useQualificationList.js'
import { useQualificationDetail } from './components/qualification/useQualificationDetail.js'
import { useRetireDialog } from '@/composables/useRetireDialog.js'

const userStore = useUserStore()
const { canManageQualification, canAdminQualificationAlert } = useQualificationPermissionMatrix(userStore)

// 列表 / 筛选 / 分页 / fetch / 状态辅助
const {
  qualifications, loading, page, pageSize, total, filters,
  statusOptions, hasFilterActive, levelOptions,
  fetchQualifications, resetFilters, getStatusTagType, statusLabel
} = useQualificationList()

// 详情抽屉 / 附件操作 / 下载
const {
  detailDrawerVisible, detailQualification, detailAttachments,
  replaceDialogVisible, replaceQualificationId, replaceAttachmentId, replaceCurrentFileName,
  openDetailDrawer, refreshDetailFromList,
  handleAttachmentActionSuccess, handleAttachmentReplace, handleAttachmentUpload,
  handleAttachmentDelete, handleDetailPreview, handleDetailDownload, handleDownloadFile
} = useQualificationDetail({ qualifications, fetchQualifications })

// 批量导出 / 下载
const {
  tableRef, hasSelection, selectedCount,
  handleSelectionChange, handleBatchExport, handleBatchDownload
} = useQualificationBatch()

// 告警配置 / 扫描到期
const { alertConfigVisible, scanningExpiring, handleScanExpiring } = useQualificationBorrowSection({ httpClient: http })

// 下架确认弹窗
const { retireDialogVisible, retireTarget, openRetireDialog, submitRetire } = useRetireDialog({
  httpClient: http,
  onRetired: fetchQualifications
})

// 表单弹窗
const formVisible = ref(false)
const editData = ref(null)
const batchUploadVisible = ref(false)
const importCombinedVisible = ref(false)

const openEdit = (row) => { editData.value = row; formVisible.value = true }
const handleRetire = openRetireDialog
const handleRetireConfirm = ({ reason }) => submitRetire(reason)

const handleRestore = async (row) => {
  try {
    await ElMessageBox.confirm('确认恢复该资质证书？', '恢复在库')
    await http.post(`/api/knowledge/qualifications/${row.id}/restore`)
    ElMessage.success('已恢复'); fetchQualifications()
  } catch { /* cancelled */ }
}

const handleRowClick = (row) => { if (row) openDetailDrawer(row) }
const handleDetailEdit = (row) => { detailDrawerVisible.value = false; openEdit(row) }
const handleDetailRetire = (row) => { detailDrawerVisible.value = false; handleRetire(row) }
const handleDetailRestore = (row) => { detailDrawerVisible.value = false; handleRestore(row) }

const handleFormSaved = () => {
  // CO-155 fix: 保存后重置 page=1，确保新数据（按 id desc 排第 1 页）立刻可见
  page.value = 1
  fetchQualifications()
  if (detailDrawerVisible.value) refreshDetailFromList()
}
</script>

<style scoped lang="scss">
.qualification-container {
  background: var(--bg-page);
  min-height: 100vh;
}
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; h2 { font-weight:600; color:var(--text-primary); margin:0 } }
.page-actions { display: flex; gap: 12px; flex-wrap: wrap; }
.premium-btn { background: linear-gradient(135deg, var(--brand-xiyu-logo) 0%, var(--brand-xiyu-logo-active) 100%); border: none; box-shadow: 0 4px 6px -1px var(--brand-xiyu-logo-shadow); transition: all 0.3s ease; &:hover { transform: translateY(-2px); box-shadow: 0 6px 8px -1px var(--brand-xiyu-logo-shadow); } }
.filter-card,.data-card { border-radius:8px; border:1px solid var(--el-border-color-lighter); box-shadow:var(--shadow-sm); margin-bottom:12px }
.pagination-wrap { display:flex; justify-content:flex-end; margin-top:16px }
.cert-name { font-weight:500; color:var(--text-primary) }
.text-muted { color:var(--text-lighter) }
.retired-tag { text-decoration:line-through; opacity:0.6 }
.batch-toolbar { display:flex; align-items:center; gap:12px; margin-bottom:12px; padding:8px 12px; background:var(--el-fill-color-light); border-radius:6px; border:1px solid var(--el-border-color-lighter) }
.batch-info { margin-left:auto; color:var(--el-text-color-secondary); font-size:13px }
</style>
