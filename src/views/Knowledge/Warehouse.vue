<template>
  <div class="warehouse-container">
    <WarehouseFilterBar
      v-model:filters="filters"
      :total="total"
      @search="resetPageAndLoad"
      @reset="resetFilters"
      @create="openCreate"
    />
    <el-card class="data-card" shadow="never">
      <el-table :data="records" v-loading="loading" style="width:100%" @row-click="openDrawer">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="name" label="仓库名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="仓库类型" width="80" align="center">
          <template #default="s"><el-tag size="small">{{ s.row.type === 'SELF_OPERATED' ? '自营' : '云仓' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="region" label="所属区域" width="80" />
        <el-table-column prop="province" label="所在省份" width="80" />
        <el-table-column prop="address" label="具体地址" min-width="160" show-overflow-tooltip />
        <el-table-column prop="area" label="面积(㎡)" width="90" align="right" />
        <el-table-column label="到期天数" width="100" align="center">
          <template #default="s"><el-tag :type="getDaysTag(s.row)">{{ computeDays(s.row) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="s"><el-tag :type="getStatusTag(s.row.status)">{{ statusLabel(s.row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="s">
            <el-button link type="primary" size="small" @click.stop="openEdit(s.row)">编辑</el-button>
            <el-button v-if="s.row.status !== 'CLOSED'" link type="danger" size="small" @click.stop="handleClose(s.row)">关仓</el-button>
            <el-button v-if="s.row.status === 'CLOSED'" link type="success" size="small" @click.stop="handleRestore(s.row)">恢复</el-button>
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
      :init-tab="activeTab"
      @submitted="load"
    />
    <WarehouseDrawer v-model="drawerVisible" :warehouse-id="detailId" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/client'
import WarehouseFilterBar from '@/components/warehouse/WarehouseFilterBar.vue'
import WarehouseDialog from '@/components/warehouse/WarehouseDialog.vue'
import WarehouseDrawer from '@/components/warehouse/WarehouseDrawer.vue'

const records = ref([]); const loading = ref(false)
const page = ref(1); const size = ref(15); const total = ref(0)
const dialogVisible = ref(false); const drawerVisible = ref(false)
const activeTab = ref('basic'); const editingId = ref(null); const detailId = ref(null)

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
  if (f.statuses?.length) p.statuses = f.statuses
  if (f.province) p.province = f.province
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
  activeTab.value = 'basic'; editingId.value = null; dialogVisible.value = true
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
  activeTab.value = 'basic'; editingId.value = row.id; dialogVisible.value = true
}

const openDrawer = (row) => { detailId.value = row.id; drawerVisible.value = true }

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
.warehouse-container { padding: 24px; }
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; h2 { font-weight:600; color:#1f2937; margin:0 } }
.data-card { border-radius:8px; border:1px solid var(--el-border-color-lighter); box-shadow:0 2px 8px rgba(0,0,0,.05) }
.pagination-wrap { display:flex; justify-content:flex-end; margin-top:16px }
</style>
