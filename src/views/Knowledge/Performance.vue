<template>
  <div class="performance-container">
    <div class="page-header">
      <div class="header-left">
        <h2>业绩管理</h2>
        <span class="sub-title">合同台账与到期提醒中心</span>
      </div>
      <div class="header-right">
        <el-button v-if="canAdminPerformanceAlert" class="ghost-btn" @click="openAlertConfig">
          <el-icon class="btn-icon"><Bell /></el-icon> 提醒配置
        </el-button>
        <el-button v-if="canManagePerformance" type="primary" class="gradient-btn" @click="openForm(null)">
          <el-icon class="btn-icon"><Plus /></el-icon> 新增业绩
        </el-button>
        <el-button v-if="canManagePerformance" class="ghost-btn" @click="handleImport">
          <el-icon class="btn-icon"><Upload /></el-icon> 批量导入
        </el-button>
        <el-button class="ghost-btn" @click="openSimilarSearch">
          <el-icon class="btn-icon"><Search /></el-icon> 相似业绩
        </el-button>
        <el-dropdown v-if="canManagePerformance" split-button class="ghost-btn export-dropdown" @click="handleExport()" @command="handleExport">
          <el-icon class="btn-icon"><Download /></el-icon> 导出
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="excel">导出 Excel</el-dropdown-item>
              <el-dropdown-item command="zip">导出 ZIP（含附件）</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <el-card class="filter-card border-glow">
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
            <el-option label="办公" value="OFFICE" />
            <el-option label="综合" value="COMPREHENSIVE" />
            <el-option label="集采" value="CENTRALIZED" />
            <el-option label="工业品" value="INDUSTRIAL" />
            <el-option label="其他" value="OTHER" />
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

    <el-card class="table-card border-glow" v-loading="loading">
      <el-table :data="records" stripe style="width: 100%" @row-click="openDetail" class="custom-table">
        <el-table-column type="selection" width="55" />
        <el-table-column type="index" label="序号" width="110" align="center" />
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
        <el-table-column prop="status" label="状态" width="95" align="center">
          <template #default="{ row }"><el-tag :type="getStatusTagType(row.status)" effect="dark">{{ row.statusLabel }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="canManagePerformance" type="primary" link size="small" @click.stop="openForm(row)">编辑</el-button>
            <el-button v-if="canManagePerformance" type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <PerformanceDetailDrawer v-if="current" v-model:visible="detailVisible" :data="current" />

    <PerformanceFormDialog v-model:visible="formVisible" :data="editingRow" :submitting="submitting" @submit="handleSubmit" />

    <PerformanceAlertConfigDialog v-model="alertConfigVisible" />

    <PerformanceSimilarDrawer v-model="similarVisible" :records="similarRecords" :loading="similarLoading" />

    <el-dialog v-model="importVisible" title="批量导入业绩" width="500px">
      <el-steps :active="importStep" finish-status="success" simple>
        <el-step title="下载模板" /><el-step title="上传文件" /><el-step title="完成" />
      </el-steps>
      <div v-if="importStep === 0" class="import-step">
        <p>请先下载导入模板，按模板格式填写业绩数据后上传。</p>
        <el-button type="primary" @click="downloadTemplate"><el-icon><Download /></el-icon> 下载模板</el-button>
      </div>
      <div v-else-if="importStep === 1" class="import-step">
        <el-upload drag :auto-upload="false" :on-change="onImportFileChange" accept=".xlsx,.xls">
          <el-icon class="el-icon--upload"><Upload /></el-icon>
          <div class="el-upload__text">拖拽文件到此处或 <em>点击上传</em></div>
        </el-upload>
      </div>
      <div v-else class="import-step">
        <el-result :icon="importResult.failureCount > 0 ? 'warning' : 'success'"
          :title="`成功 ${importResult.successCount} 条，失败 ${importResult.failureCount} 条`">
          <template #sub-title>
            <div v-if="importResult.failures.length > 0" class="import-failures">
              <p v-for="f in importResult.failures.slice(0,5)" :key="f.rowNum">第 {{ f.rowNum }} 行: {{ f.reason }}</p>
            </div>
          </template>
        </el-result>
      </div>
      <template #footer>
        <el-button v-if="importStep === 1 && importFile" type="primary" @click="confirmImport" :loading="importLoading">确认导入</el-button>
        <el-button v-if="importStep === 2" @click="importVisible = false; importStep = 0">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { performanceApi } from '@/api/modules/performance.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Download, Bell, Search } from '@element-plus/icons-vue'
import { usePerformanceImport } from '@/composables/usePerformanceImport.js'
import { useKnowledgePermission } from '@/composables/useKnowledgePermission'
import PerformanceDetailDrawer from './components/PerformanceDetailDrawer.vue'
import PerformanceFormDialog from './components/PerformanceFormDialog.vue'
import PerformanceAlertConfigDialog from './components/performance/PerformanceAlertConfigDialog.vue'
import PerformanceSimilarDrawer from './components/PerformanceSimilarDrawer.vue'

const { canManagePerformance, canAdminAlert: canAdminPerformanceAlert } = useKnowledgePermission()

// Page state
const searchForm = reactive({ keyword: '', customerTypes: [], projectTypes: [], statuses: [], customerLevels: [], territory: '', signingDateRange: null, expiryDateRange: null, hasBidNotice: null, projectManagerKeyword: '' })
const loading = ref(false); const records = ref([]); const current = ref(null)
const detailVisible = ref(false); const editingRow = ref(null); const formVisible = ref(false)
const alertConfigVisible = ref(false); const submitting = ref(false)
const similarVisible = ref(false); const similarRecords = ref([]); const similarLoading = ref(false)
const openSimilarSearch = async () => {
  similarLoading.value = true
  similarVisible.value = true
  try {
    const similarForm = { ...searchForm, keyword: '' }
    const { data } = await performanceApi.getList(similarForm)
    const scored = (data || []).map(r => {
      let score = 0
      if (searchForm.customerTypes?.length > 0 && searchForm.customerTypes.includes(r.customerType)) score += 3
      if (searchForm.projectTypes?.length > 0 && searchForm.projectTypes.includes(r.projectType)) score += 2
      if (searchForm.customerLevels?.length > 0 && searchForm.customerLevels.includes(r.customerLevel)) score += 1
      if (searchForm.territory && r.territory?.includes(searchForm.territory)) score += 2
      return { ...r, _similarScore: score }
    })
    similarRecords.value = scored.sort((a, b) => b._similarScore - a._similarScore).slice(0, 20)
  } catch {
    ElMessage.error('相似业绩搜索失败')
  } finally {
    similarLoading.value = false
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const { data } = await performanceApi.getList(searchForm)
    records.value = data || []
  } catch {
    ElMessage.error('台账加载失败，请检查服务状态')
  } finally {
    loading.value = false
  }
}

const {
  importVisible, importStep, importFile, importLoading, importResult,
  openImport: handleImport, downloadTemplate, onImportFileChange, confirmImport
} = usePerformanceImport(loadData)

const getCustomerTypeTagType = (t) => t === 'CENTRAL_SOE' ? 'danger' : t === 'LOCAL_SOE' ? 'warning' : t === 'GOVERNMENT_INSTITUTION' ? 'success' : 'primary'
const getStatusTagType = (s) => s === 'EXPIRED' ? 'danger' : s === 'EXPIRING' ? 'warning' : 'success'
const getExpiryDateClass = (row) => row.status === 'EXPIRED' ? 'text-danger' : row.status === 'EXPIRING' ? 'text-warning' : 'text-normal'
const getDaysRemainingClass = (row) => (row.daysRemaining != null && row.daysRemaining < 0) ? 'text-danger' : row.status === 'EXPIRING' ? 'text-warning' : 'text-success'
const formatDaysRemaining = (days) => (days == null || days > 999999999 || days === 2147483647) ? '-' : days < 0 ? `已逾期 ${Math.abs(days)} 天` : `${days} 天`

const resetFilters = () => {
  Object.assign(searchForm, {
    keyword: '', customerTypes: [], projectTypes: [], statuses: [], customerLevels: [],
    territory: '', signingDateRange: null, expiryDateRange: null,
    hasBidNotice: null, projectManagerKeyword: ''
  })
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
  try {
    if (command === 'zip') { await performanceApi.batchExportZip(); ElMessage.success('ZIP 导出成功') }
    else { await performanceApi.batchExport(); ElMessage.success('导出成功') }
  } catch (err) { console.error('Export failed:', err); ElMessage.error('导出失败: ' + (err?.message || '未知错误')) }
}

onMounted(loadData)
</script>
<style scoped lang="scss" src="./components/Performance.scss"></style>
<style scoped>
.import-step { text-align: center; padding: 24px 0; }
.import-failures { text-align: left; color: var(--el-color-danger); font-size: 13px; max-height: 120px; overflow-y: auto; }
</style>
