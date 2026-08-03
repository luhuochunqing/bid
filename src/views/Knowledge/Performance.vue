<template>
  <div class="performance-container">
    <div class="kb-page-header">
      <div>
        <h2>业绩管理</h2>
      </div>
      <div class="kb-header-actions">
        <el-button v-if="canAdminPerformanceAlert" @click="openAlertConfig">
          <el-icon class="btn-icon"><Bell /></el-icon> 提醒配置
        </el-button>
        <el-button v-if="canManagePerformance" type="primary" @click="openForm(null)">
          <el-icon class="btn-icon"><Plus /></el-icon> 新增业绩
        </el-button>
        <el-button v-if="canManagePerformance" @click="handleImport">
          <el-icon class="btn-icon"><Upload /></el-icon> 批量导入
        </el-button>
        <el-dropdown v-if="canManagePerformance" split-button @click="handleExport()" @command="handleExport">
          <el-icon class="btn-icon"><Download /></el-icon> 导出
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="excel">导出 Excel</el-dropdown-item>
              <el-dropdown-item command="zip">导出 ZIP（含附件）</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <!-- CO-582: 独立的业绩合订本导出入口（与 ZIP 导出分离） -->
        <el-button v-if="canManagePerformance" type="success" @click="openBundleExport">
          <el-icon class="btn-icon"><Document /></el-icon> 导出合订本
        </el-button>
      </div>
    </div>

    <el-card class="filter-card kb-filter-card">
      <el-form :inline="true" :model="searchForm" class="demo-form-inline">
        <el-form-item label="模糊搜索">
          <el-input v-model="searchForm.keyword" placeholder="合同名称/签约单位/集团名称" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item label="客户类型">
          <el-select v-model="searchForm.customerTypes" placeholder="全部" clearable multiple collapse-tags style="width: 180px">
            <el-option label="政府机关/事业单位" value="GOVERNMENT_INSTITUTION" />
            <el-option label="央企" value="CENTRAL_SOE" />
            <el-option label="地方国企" value="LOCAL_SOE" />
            <el-option label="民企" value="PRIVATE_ENTERPRISE" />
            <el-option label="港澳台/外企" value="FOREIGN_HK_MACAO_TW" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类型">
          <el-select v-model="searchForm.projectTypes" placeholder="全部" clearable multiple collapse-tags style="width: 160px">
            <el-option v-for="opt in PROJECT_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同状态">
          <el-select v-model="searchForm.statuses" placeholder="全部" clearable multiple collapse-tags style="width: 160px">
            <el-option label="履约中" value="IN_PERFORMANCE" />
            <el-option label="即将到期" value="EXPIRING" />
            <el-option label="已到期" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户级别">
          <el-select v-model="searchForm.customerLevels" placeholder="全部" clearable multiple collapse-tags style="width: 140px">
            <el-option label="集团" value="GROUP" />
            <el-option label="二级单位" value="SUBSIDIARY" />
          </el-select>
        </el-form-item>
        <el-form-item label="属地">
          <el-input v-model="searchForm.territory" placeholder="省/市关键词" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="签约日期">
          <el-date-picker v-model="searchForm.signingDateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="searchForm.expiryDateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
        </el-form-item>
        <el-form-item label="中标通知书">
          <el-select v-model="searchForm.hasBidNotice" placeholder="全部" clearable style="width: 100px">
            <el-option label="有" value="true" /><el-option label="无" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目负责人">
          <el-input v-model="searchForm.projectManagerKeyword" placeholder="负责人姓名" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card kb-table-card" v-loading="loading">
      <el-table :data="pagedRecords" stripe style="width: 100%" max-height="calc(100vh - 300px)" scrollbar-always-on @row-click="openDetail" @selection-change="handleSelectionChange" class="custom-table">
        <el-table-column type="selection" width="55" />
        <el-table-column type="index" label="序号" width="110" align="center" :index="indexMethod" />
        <el-table-column prop="contractName" label="合同名称" min-width="180" />
        <el-table-column prop="signingEntity" label="签约单位" min-width="160" />
        <el-table-column prop="customerType" label="客户类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getCustomerTypeTagType(row.customerType)" effect="light">{{ row.customerTypeLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="groupCompany" label="集团公司" min-width="150" />
        <el-table-column prop="projectType" label="项目类型" width="120" align="center">
          <template #default="{ row }"><el-tag type="info" size="small">{{ row.projectTypeLabel }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="customerLevel" label="客户级别" width="120" align="center">
          <template #default="{ row }"><el-tag type="warning" size="small">{{ row.customerLevelLabel }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="signingDate" label="签约日期" width="140" align="center" />
        <el-table-column prop="expiryDate" label="截止日期" width="120" align="center">
          <template #default="{ row }"><span :class="getExpiryDateClass(row)">{{ row.expiryDate }}</span></template>
        </el-table-column>
        <el-table-column prop="daysRemaining" label="到期天数" width="120" align="center">
          <template #default="{ row }">
            <span :class="getDaysRemainingClass(row)" style="font-weight: 600">{{ formatDaysRemaining(row.daysRemaining) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="groupTotalExpiryDate" label="总截止日期" width="130" align="center">
          <template #default="{ row }">
            <span :class="getGroupTotalExpiryDateClass(row.groupTotalExpiryDate)">{{ row.groupTotalExpiryDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="95" align="center">
          <template #default="{ row }"><el-tag :type="getStatusTagType(row.status)" effect="dark">{{ row.statusLabel }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center" class-name="kb-action-col">
          <template #default="{ row }">
            <el-button v-if="canManagePerformance" type="primary" link size="small" @click.stop="openForm(row)">编辑</el-button>
            <el-button v-if="canManagePerformance" type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="kb-pagination-wrap">
        <el-pagination
          v-if="totalCount > 0"
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="pageSizes"
          :total="totalCount"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <PerformanceDetailDrawer v-if="current" v-model:visible="detailVisible" :data="current" />
    <PerformanceFormDialog v-model:visible="formVisible" :data="editingRow" :submitting="submitting" @submit="handleSubmit" />
    <PerformanceAlertConfigDialog v-model="alertConfigVisible" />
    <PerformanceImportDialog v-model="importVisible" @imported="loadData" />
    <PerformanceExportZipDialog
      v-model:visible="exportZipDialogVisible"
      :selected-count="selectedIds.length"
      :total-count="records.length"
      @confirm="handleExportZipConfirm"
    />
    <!-- CO-582: 业绩合订本导出对话框 -->
    <PerformanceBundleExportDialog
      v-model:visible="bundleExportDialogVisible"
      :selected-ids="selectedIds"
      :total-count="records.length"
      :criteria="searchForm"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { performanceApi } from '@/api/modules/performance.js'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import { Plus, Upload, Download, Bell, Document } from '@element-plus/icons-vue'
import { useKnowledgePermission } from '@/composables/useKnowledgePermission'
import { useListPagination } from '@/composables/useListPagination'
import { PROJECT_TYPE_OPTIONS } from '@/constants/projectTypes.js'
import { sortPerformanceByGroupPinyin } from './performanceSort.js'
import PerformanceDetailDrawer from './components/PerformanceDetailDrawer.vue'
import PerformanceFormDialog from './components/PerformanceFormDialog.vue'
import PerformanceAlertConfigDialog from './components/performance/PerformanceAlertConfigDialog.vue'
import PerformanceImportDialog from './components/PerformanceImportDialog.vue'
import PerformanceExportZipDialog from './components/PerformanceExportZipDialog.vue'
import PerformanceBundleExportDialog from './components/PerformanceBundleExportDialog.vue'

const { canManagePerformance, canAdminAlert: canAdminPerformanceAlert } = useKnowledgePermission()
// Page state
const searchForm = reactive({ keyword: '', customerTypes: [], projectTypes: [], statuses: [], customerLevels: [], territory: '', signingDateRange: null, expiryDateRange: null, hasBidNotice: null, projectManagerKeyword: '' })
const loading = ref(false); const records = ref([]); const current = ref(null)
const detailVisible = ref(false); const editingRow = ref(null); const formVisible = ref(false)
const alertConfigVisible = ref(false); const submitting = ref(false)
const selectedIds = ref([]); const handleSelectionChange = (rows) => { selectedIds.value = rows.map(r => r.id) }
const {
  pagination, pageSizes, totalCount, pagedData: pagedRecords,
  handleSizeChange, resetPage
} = useListPagination(records)
// 分页序号
const indexMethod = (index) => (pagination.value.page - 1) * pagination.value.pageSize + index + 1

const loadData = async () => {
  loading.value = true
  try {
    const { data } = await performanceApi.getList(searchForm)
    records.value = sortPerformanceByGroupPinyin(data || [])
    resetPage()
  } catch {
    ElMessage.error('台账加载失败，请检查服务状态')
  } finally {
    loading.value = false
  }
}

const importVisible = ref(false)
const handleImport = () => { importVisible.value = true }
const exportZipDialogVisible = ref(false)
// CO-582: 业绩合订本导出对话框状态
const bundleExportDialogVisible = ref(false)
const openBundleExport = () => { bundleExportDialogVisible.value = true }
const getCustomerTypeTagType = (t) => t === 'CENTRAL_SOE' ? 'danger' : t === 'LOCAL_SOE' ? 'warning' : t === 'GOVERNMENT_INSTITUTION' ? 'success' : 'primary'
const getStatusTagType = (s) => s === 'EXPIRED' ? 'danger' : s === 'EXPIRING' ? 'warning' : 'success'
const getExpiryDateClass = (row) => row.status === 'EXPIRED' ? 'text-danger' : row.status === 'EXPIRING' ? 'text-warning' : 'text-normal'
// CO-583: 总截止日期样式基于聚合值自身判断，不受单条合同 status 影响
const getGroupTotalExpiryDateClass = (groupTotalExpiryDate) => {
  if (!groupTotalExpiryDate) return 'text-normal'
  const today = new Date().toISOString().slice(0, 10)
  return groupTotalExpiryDate < today ? 'text-danger' : 'text-normal'
}
const getDaysRemainingClass = (row) => (row.daysRemaining != null && row.daysRemaining < 0) ? 'text-danger' : row.status === 'EXPIRING' ? 'text-warning' : 'text-success'
const formatDaysRemaining = (days) => (days == null) ? '-' : days < 0 ? `已逾期 ${Math.abs(days)} 天` : `${days} 天`
const resetFilters = () => {
  Object.assign(searchForm, {
    keyword: '', customerTypes: [], projectTypes: [], statuses: [], customerLevels: [],
    territory: '', signingDateRange: null, expiryDateRange: null,
    hasBidNotice: null, projectManagerKeyword: ''
  })
  resetPage()
  loadData()
}
const openDetail = (row) => { current.value = row; detailVisible.value = true }
const openForm = (row) => { editingRow.value = row; formVisible.value = true }
const openAlertConfig = () => { alertConfigVisible.value = true }
const handleSubmit = async (formData) => {
  submitting.value = true
  // CO-442: attachmentMap 改为 Map<fileType, Array>，展平时 flatMap 多文件
  const payload = {
    ...formData,
    attachments: Object.keys(formData.attachmentMap)
      .flatMap(type => (formData.attachmentMap[type] || [])
        .map(f => ({ fileName: f.fileName, fileUrl: f.fileUrl, fileType: type })))
  }
  try {
    if (editingRow.value) {
      await performanceApi.update(formData.id, payload)
      ElMessage.success('业绩档案更新成功')
    } else {
      await performanceApi.create(payload)
      ElMessage.success('业绩档案创建成功')
    }
    formVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    submitting.value = false
  }
}
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`您确定要删除合同「${row.contractName}」的业绩档案吗？`, '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await performanceApi.delete(row.id)
    ElMessage.success('业绩档案删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败，请重试')
  }
}
const handleExport = async (command) => {
  if (command === 'zip') {
    exportZipDialogVisible.value = true
    return
  }
  try {
    const params = { ...searchForm, ids: selectedIds.value.length ? selectedIds.value : undefined }
    await performanceApi.batchExport(params)
    ElMessage.success('导出成功')
  } catch (err) { console.error('Export failed:', err); ElMessage.error('导出失败: ' + (err?.message || '未知错误')) }
}

const handleExportZipConfirm = async (checkedTypes) => {
  exportZipDialogVisible.value = false
  const loading = ElLoading.service({ lock: true, text: '正在打包，请稍候...', background: 'rgba(0, 0, 0, 0.7)' })
  try {
    const params = { ...searchForm, ids: selectedIds.value.length ? selectedIds.value : undefined, attachmentTypes: checkedTypes }
    await performanceApi.batchExportZip(params)
    ElMessage.success('ZIP 导出成功')
  } catch (err) {
    console.error('Export ZIP failed:', err)
    ElMessage.error('导出失败: ' + (err?.message || '未知错误'))
  } finally {
    loading.close()
  }
}

onMounted(loadData)
</script>
<style scoped lang="scss" src="./components/Performance.scss"></style>
