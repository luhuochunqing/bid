<template>
  <div class="warehouse-container">
    <WarehouseFilterBar
      v-model:filters="filters"
      :total="total"
      :selected-count="selectedRows.length"
      @search="resetPageAndLoad"
      @reset="resetFilters"
      @realtime-search="resetPageAndLoad"
      @create="openCreate"
      @export="exportVisible = true"
      @import="importVisible = true"
      @batch-export="handleBatchExport"
      @ledger-export="ledgerExportVisible = true"
    />
    <el-card class="data-card" shadow="never">
      <el-table :data="records" v-loading="loading" style="width:100%" @row-click="openDrawer"
        :row-class-name="({row}) => newlyCreatedIds.has(row.id) ? 'row-newly-created' : ''"
        @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" :selectable="r => r.status !== 'CLOSED'" />
        <el-table-column type="index" label="序号" width="110" />
        <el-table-column prop="name" label="仓库名称" min-width="160">
          <template #default="s"><span class="warehouse-name">{{ s.row.name }}</span></template>
        </el-table-column>
        <el-table-column label="仓库类型" width="120" align="center">
          <template #default="s"><el-tag size="small">{{ s.row.type === 'SELF_OPERATED' ? '自营' : '云仓' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始时间" width="110">
          <template #default="s">{{ formatDateMonth(s.row.startDate) }}</template>
        </el-table-column>
        <el-table-column label="所属区域" width="120" align="center">
          <template #default="s"><el-tag size="small">{{ s.row.region }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="province" label="所在省份" width="120" />
        <el-table-column prop="address" label="具体地址" min-width="160" />
        <el-table-column prop="area" label="面积(㎡)" width="120" align="right" />
        <el-table-column label="到期天数" width="110" align="center">
          <template #default="s"><el-tag :type="getDaysTag(s.row)">{{ computeDays(s.row) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="s"><el-tag :type="getStatusTag(s.row.status)">{{ statusLabel(s.row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="产权证" width="90" align="center">
          <template #default="s">{{ s.row.hasPropertyCert ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="发票" width="80" align="center">
          <template #default="s">{{ s.row.hasInvoice ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="照片" width="80" align="center">
          <template #default="s">{{ s.row.hasPhotos ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="s">
            <el-button v-if="canManage" link type="primary" size="small" @click.stop="openEdit(s.row)">编辑</el-button>
            <el-button v-if="canManage && s.row.status !== 'CLOSED'" link type="danger" size="small" @click.stop="handleClose(s.row)">关仓</el-button>
            <el-button v-if="canManage && s.row.status === 'CLOSED'" link type="success" size="small" @click.stop="handleRestore(s.row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[15,30,50,100]" :total="total"
          layout="total,sizes,prev,pager,next" @size-change="resetPageAndLoad" @current-change="load" />
      </div>
    </el-card>

    <WarehouseDialog
      v-if="dialogVisible"
      v-model="dialogVisible"
      :editing-id="editingId"
      :form="form"
      @submitted="handleSubmitted"
    />
    <WarehouseDrawer v-model="drawerVisible" :warehouse-id="detailId" @edit="handleDrawerEdit" />
    <WarehouseExportDialog v-model="exportVisible" :filters="exportFilters" :mode="exportMode" :selected-ids="selectedRowIds" />
    <WarehouseImportDialog v-model="importVisible" @imported="handleImported" @download-template="handleDownloadTemplate" />
    <WarehouseLedgerExportDialog
      v-model="ledgerExportVisible"
      :filter="filters"
      :filter-count="total"
      :all-in-use-count="allInUseCount"
      :selected-ids="selectedRowIds"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/client'
import { useKnowledgePermission } from '@/composables/useKnowledgePermission'
import WarehouseFilterBar from '@/components/warehouse/WarehouseFilterBar.vue'
import WarehouseDialog from '@/components/warehouse/WarehouseDialog.vue'
import WarehouseDrawer from '@/components/warehouse/WarehouseDrawer.vue'
import WarehouseExportDialog from '@/components/warehouse/WarehouseExportDialog.vue'
import WarehouseImportDialog from '@/components/warehouse/WarehouseImportDialog.vue'
import WarehouseLedgerExportDialog from '@/components/warehouse/WarehouseLedgerExportDialog.vue'

const { canManage } = useKnowledgePermission()

const records = ref([]); const loading = ref(false)
const page = ref(1); const size = ref(15); const total = ref(0)

const dialogVisible = ref(false); const drawerVisible = ref(false)
const editingId = ref(null); const detailId = ref(null)
const exportVisible = ref(false)
const importVisible = ref(false)
const ledgerExportVisible = ref(false)
const allInUseCount = ref(0)
const selectedRows = ref([])
const selectedRowIds = ref([])
const exportMode = ref('filter')
const exportFilters = ref({})
const newlyCreatedIds = ref(new Set())

const filters = ref({})
const form = reactive({
  name:'', type:'SELF_OPERATED', region:'华东', province:'', address:'', area:0, contactPerson:'', remarks:'',
  startDate:null, endDate:null, lessor:'', lessee:'西域', invoicePeriod:'', closePlan:'',
  hasPropertyCert:false, hasInvoice:false, hasPhotos:false, certRemarks:''
})

const STATUS_MAP = { IN_USE:'使用中', EXPIRING:'即将到期', EXPIRED:'已过期', CLOSED:'已关仓' }

const buildParams = () => {
  const p = { page: page.value - 1, size: size.value }
  const f = filters.value
  if (f.keyword) p.keyword = f.keyword
  if (f.types?.length) p.types = f.types
  // 默认排除已关仓（CLOSED）。仅当用户显式选中"已关仓"或勾选"包含已关仓"时才传 statuses。
  const includeClosed = f.includeClosed === true
  const hasClosed = f.statuses?.includes('CLOSED')
  if (f.statuses?.length && (includeClosed || hasClosed)) {
    p.statuses = f.statuses
  } else {
    p.statuses = ['IN_USE', 'EXPIRING', 'EXPIRED']
  }
  if (f.regions?.length) p.regions = f.regions
  if (f.provinces?.length) p.provinces = f.provinces
  if (f.endDateFrom) p.endDateFrom = f.endDateFrom
  if (f.endDateTo) p.endDateTo = f.endDateTo
  if (f.hasPropertyCert) p.hasPropertyCert = true
  if (f.hasInvoice) p.hasInvoice = true
  if (f.hasPhotos) p.hasPhotos = true
  if (f.contactPersonKeyword) p.contactPersonKeyword = f.contactPersonKeyword
  return p
}

const load = async () => {
  loading.value = true
  try {
    const params = buildParams()
    const { data } = await http.get('/api/knowledge/warehouses', { params })
    records.value = data.content || []
    total.value = data.totalElements || 0
  } catch {} finally { loading.value = false }
}

const resetPageAndLoad = () => { page.value = 1; load() }
const resetFilters = () => { filters.value = {}; resetPageAndLoad() }

const openCreate = () => {
  Object.assign(form, {
    name:'', type:'SELF_OPERATED', region:'华东', province:'', address:'', area:0, contactPerson:'', remarks:'',
    startDate:null, endDate:null, lessor:'', lessee:'西域', invoicePeriod:'', closePlan:'',
    hasPropertyCert:false, hasInvoice:false, hasPhotos:false, certRemarks:''
  })
  editingId.value = null; dialogVisible.value = true
}

const openEdit = (row) => {
  Object.assign(form, {
    name: row.name || '', type: row.type || 'SELF_OPERATED', region: row.region || '', province: row.province || '',
    address: row.address || '', area: row.area || 0, contactPerson: row.contactPerson || '', remarks: row.remarks || '',
    startDate: row.startDate, endDate: row.endDate, lessor: row.lessor || '', lessee: row.lessee || '',
    invoicePeriod: row.invoicePeriod || '', closePlan: row.closePlan || '',
    hasPropertyCert: row.hasPropertyCert || false, hasInvoice: row.hasInvoice || false, hasPhotos: row.hasPhotos || false,
    certRemarks: row.certRemarks || ''
  })
  editingId.value = row.id; dialogVisible.value = true
}

const openDrawer = (row) => { detailId.value = row.id; drawerVisible.value = true }

const handleDrawerEdit = (row) => {
  drawerVisible.value = false
  Object.assign(form, {
    name: row.name || '', type: row.type || 'SELF_OPERATED', region: row.region || '', province: row.province || '',
    address: row.address || '', area: row.area || 0, contactPerson: row.contactPerson || '', remarks: row.remarks || '',
    startDate: row.startDate, endDate: row.endDate, lessor: row.lessor || '', lessee: row.lessee || '',
    invoicePeriod: row.invoicePeriod || '', closePlan: row.closePlan || '',
    hasPropertyCert: row.hasPropertyCert || false, hasInvoice: row.hasInvoice || false, hasPhotos: row.hasPhotos || false,
    certRemarks: row.certRemarks || ''
  })
  editingId.value = row.id; dialogVisible.value = true
}

const handleClose = async (row) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入关仓原因', '关仓确认', {
      confirmButtonText: '确认关仓', cancelButtonText: '取消', type: 'warning',
      inputPlaceholder: '请填写关仓原因（必填）', inputValidator: (v) => !!v?.trim() || '关仓原因不能为空'
    })
    await http.post(`/api/knowledge/warehouses/${row.id}/close`, { reason })
    ElMessage.success('已关仓'); load()
  } catch {}
}

const handleRestore = async (row) => {
  try {
    await ElMessageBox.confirm('确认恢复该仓库？', '恢复确认')
    await http.post(`/api/knowledge/warehouses/${row.id}/restore`)
    ElMessage.success('已恢复'); load()
  } catch {}
}

const handleSubmitted = async (newId) => {
  await load()
  if (newId) {
    newlyCreatedIds.value.add(newId)
    setTimeout(() => newlyCreatedIds.value.delete(newId), 3000)
  }
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
  selectedRowIds.value = rows.map(r => r.id)
}

const handleDownloadTemplate = async () => {
  try {
    const response = await http.get('/api/knowledge/warehouses/import/template', { responseType: 'blob' })
    const blob = response.data
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '仓库导入模板.xlsx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    ElMessage.success('模板已下载')
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '模板下载失败')
  }
}

const handleBatchExport = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先勾选要导出的仓库')
    return
  }
  exportMode.value = 'ids'
  exportFilters.value = { ids: selectedRowIds.value }
  exportVisible.value = true
}

const handleImported = () => {
  selectedRows.value = []
  load()
}

const formatDateMonth = (d) => {
  if (!d) return ''
  const parts = d.split('-')
  return parts.length >= 2 ? `${parts[0]}-${parts[1]}` : d
}
const computeDays = (r) => {
  if (!r.endDate) return '—'
  const d = Math.ceil((new Date(r.endDate) - Date.now()) / 86400000)
  return d < 0 ? `已过期${-d}天` : `${d}天`
}
const getDaysTag = (r) => {
  if (!r.endDate) return ''
  const d = Math.ceil((new Date(r.endDate) - Date.now()) / 86400000)
  return d < 0 ? 'danger' : d <= 30 ? 'warning' : 'success'
}
const getStatusTag = (s) => s === 'IN_USE' ? 'success' : s === 'EXPIRING' ? 'warning' : s === 'EXPIRED' ? 'danger' : 'info'
const statusLabel = (s) => STATUS_MAP[s] || s

onMounted(load)
</script>

<style scoped lang="scss">
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; h2 { font-weight:600; color:#1f2937; margin:0 } }
.data-card { border-radius:8px; border:1px solid var(--el-border-color-lighter); box-shadow:0 2px 8px rgba(0,0,0,.05) }
.pagination-wrap { display:flex; justify-content:flex-end; margin-top:16px }
:deep(.row-newly-created) { animation: highlightFade 3s ease-out }
@keyframes highlightFade {
  0% { background-color: #e1f3d8 }
  100% { background-color: transparent }
}
</style>
