<template>
  <div class="brandauth-container">
    <div class="page-header"><h2>品牌授权</h2></div>
    <div class="tab-toolbar">
      <el-button v-if="canManage" type="primary" @click="openCreate"><el-icon><Plus /></el-icon> {{ activeTab === 'agent' ? '新增代理商授权' : '新增原厂授权' }}</el-button>
      <el-button v-if="canManage" @click="handleExport"><el-icon><Download /></el-icon> 导出 Excel</el-button>
      <el-button v-if="canManage" type="success" @click="showImport"><el-icon><Upload /></el-icon> 批量导入</el-button>
    </div>
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="原厂授权" name="manufacturer">
        <el-card class="filter-card">
          <el-form :inline="true" :model="filters" size="default">
            <el-form-item label="一级产线">
              <el-select v-model="filters.productLines" multiple collapse-tags collapse-tags-tooltip filterable placeholder="全部" style="width:180px">
                <el-option v-for="p in productLineOptions" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="品牌ID"><el-input v-model="filters.brandId" placeholder="模糊搜索" clearable style="width:120px" /></el-form-item>
            <el-form-item label="品牌"><el-input v-model="filters.brandName" placeholder="模糊搜索" clearable style="width:120px" /></el-form-item>
            <el-form-item label="进口/国产">
              <el-select v-model="filters.importDomestic" clearable style="width:90px"><el-option label="进口" value="进口" /><el-option label="国产" value="国产" /></el-select>
            </el-form-item>
            <el-form-item label="品牌原厂"><el-input v-model="filters.manufacturerName" placeholder="模糊" clearable style="width:120px" /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="filters.statuses" multiple collapse-tags placeholder="默认排除已作废" style="width:150px">
                <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词"><el-input v-model="filters.keyword" placeholder="编号/备注" clearable style="width:120px" /></el-form-item>
            <el-form-item><el-button type="primary" @click="loadData">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item>
          </el-form>
        </el-card>
        <el-card class="table-card" v-loading="loading">
          <el-table :data="records" stripe @row-click="openDetail">
            <el-table-column type="selection" width="55" />
            <el-table-column type="index" label="序号" width="110" align="center" />
            <el-table-column prop="brandId" label="授权编号" width="120" />
            <el-table-column prop="productLine" label="一级产线" width="120" />
            <el-table-column prop="brandName" label="品牌" width="100" />
            <el-table-column prop="importDomestic" label="进口/国产" width="120" align="center" />
            <el-table-column prop="manufacturerName" label="品牌原厂名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="authStartDate" label="授始" width="100" />
            <el-table-column prop="authEndDate" label="授止" width="100" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{row}">
                <el-tag :type="row.statusTagType" :class="{ 'revoked-tag': row.status === 'REVOKED' }">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" :width="canManage ? 180 : 80" fixed="right" align="center">
              <template #default="{row}">
                <el-button type="primary" link size="small" @click.stop="openDetail(row)">查看</el-button>
                <el-button v-if="canManage && row.status !== 'REVOKED'" type="primary" link size="small" @click.stop="openEdit(row)">编辑</el-button>
                <el-button v-if="canManage && row.status !== 'REVOKED'" type="danger" link size="small" @click.stop="showRevokeDialog(row)">作废</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total,sizes,prev,pager,next" @size-change="loadData" @current-change="loadData" />
          </div>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="代理商授权" name="agent">
        <el-card class="filter-card">
          <el-form :inline="true" :model="filters" size="default">
            <el-form-item label="一级产线">
              <el-select v-model="filters.productLines" multiple collapse-tags collapse-tags-tooltip filterable placeholder="全部" style="width:180px">
                <el-option v-for="p in productLineOptions" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="品牌ID"><el-input v-model="filters.brandId" placeholder="模糊搜索" clearable style="width:120px" /></el-form-item>
            <el-form-item label="品牌"><el-input v-model="filters.brandName" placeholder="模糊搜索" clearable style="width:120px" /></el-form-item>
            <el-form-item label="进口/国产">
              <el-select v-model="filters.importDomestic" clearable style="width:90px"><el-option label="进口" value="进口" /><el-option label="国产" value="国产" /></el-select>
            </el-form-item>
            <el-form-item label="品牌原厂"><el-input v-model="filters.manufacturerName" placeholder="模糊" clearable style="width:120px" /></el-form-item>
            <el-form-item label="代理商名称"><el-input v-model="filters.agentName" placeholder="模糊" clearable style="width:120px" /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="filters.statuses" multiple collapse-tags placeholder="默认排除已作废" style="width:150px">
                <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词"><el-input v-model="filters.keyword" placeholder="编号/备注" clearable style="width:120px" /></el-form-item>
            <el-form-item><el-button type="primary" @click="loadData">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item>
          </el-form>
        </el-card>
        <el-card class="table-card" v-loading="loading">
          <el-table :data="records" stripe @row-click="openDetail">
            <el-table-column type="selection" width="55" />
            <el-table-column type="index" label="序号" width="110" align="center" />
            <el-table-column prop="brandId" label="授权编号" width="120" />
            <el-table-column prop="productLine" label="一级产线" width="120" />
            <el-table-column prop="brandName" label="品牌" width="100" />
            <el-table-column prop="importDomestic" label="进口/国产" width="120" align="center" />
            <el-table-column prop="manufacturerName" label="品牌原厂名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="agentName" label="代理商名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="有效期较早值" width="120" align="center">
              <template #default="{row}">
                {{ row.authEndDate }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{row}">
                <el-tag :type="row.statusTagType" :class="{ 'revoked-tag': row.status === 'REVOKED' }">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" :width="canManage ? 180 : 80" fixed="right" align="center">
              <template #default="{row}">
                <el-button type="primary" link size="small" @click.stop="openDetail(row)">查看</el-button>
                <el-button v-if="canManage && row.status !== 'REVOKED'" type="primary" link size="small" @click.stop="openEdit(row)">编辑</el-button>
                <el-button v-if="canManage && row.status !== 'REVOKED'" type="danger" link size="small" @click.stop="showRevokeDialog(row)">作废</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total,sizes,prev,pager,next" @size-change="loadData" @current-change="loadData" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <BrandAuthFormDrawer v-model="formVisible" :initial-data="editData" :mode="activeTab" @save="handleSave" @close="editData=null" />
    <BrandAuthDetailDrawer v-model="detailVisible" :detail="detail" :logs="detailLogs" @edit="onDetailEdit" @revoke="onDetailRevoke" />
    <BrandAuthRevokeDialog v-model="revokeVisible" :target="revokeTarget" @done="loadData" />
    <el-dialog v-model="exportVisible" title="导出确认" width="480px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="当前筛选条件">{{ filterSummary }}</el-descriptions-item>
        <el-descriptions-item label="导出条数">{{ total }} 条</el-descriptions-item>
        <el-descriptions-item label="文件名">{{ exportFilename }}</el-descriptions-item>
      </el-descriptions>
      <p v-if="total > 500" style="color:#f56c6c">⚠ 超出 500 条限制，请缩小筛选范围</p>
      <template #footer>
        <el-button @click="exportVisible = false">取消</el-button>
        <el-button type="primary" :disabled="total === 0 || total > 500" @click="doExport">确认导出</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Upload } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import http from '@/api/client'
import brandAuthApi, { PRODUCT_LINE_OPTIONS, STATUS_OPTIONS } from '@/api/modules/brandAuth.js'
import BrandAuthFormDrawer from './components/BrandAuthFormDrawer.vue'
import BrandAuthDetailDrawer from './components/BrandAuthDetailDrawer.vue'
import BrandAuthImportDialog from './components/BrandAuthImportDialog.vue'
import BrandAuthRevokeDialog from './components/BrandAuthRevokeDialog.vue'

const productLineOptions = PRODUCT_LINE_OPTIONS
const statusOptions = STATUS_OPTIONS

const userStore = useUserStore()
const canManage = computed(() => userStore.hasPermission('knowledge-brand-auth'))

const activeTab = ref('manufacturer')
const records = ref([]); const loading = ref(false)
const page = ref(1); const pageSize = ref(20); const total = ref(0)
const filters = reactive({ productLines:[], brandId:'', brandName:'', importDomestic:'', manufacturerName:'', agentName:'', statuses:[], keyword:'' })

const formVisible = ref(false); const editData = ref(null)
const detailVisible = ref(false); const detail = ref({}); const detailLogs = ref([])
const revokeVisible = ref(false); const revokeTarget = ref(null)
const exportVisible = ref(false)

const loadData = async () => {
  loading.value = true
  try {
    const type = activeTab.value === 'agent' ? 'AGENT' : 'MANUFACTURER'
    const { data } = await brandAuthApi.getList({ ...filters, authorizationType: type, page: page.value-1, size: pageSize.value })
    records.value = data?.content || []; total.value = data?.totalElements || 0
  } catch { ElMessage.error('加载失败') } finally { loading.value = false }
}

const resetFilters = () => {
  Object.assign(filters, { productLines:[], brandId:'', brandName:'', importDomestic:'', manufacturerName:'', agentName:'', statuses:[], keyword:'' })
  page.value = 1; loadData()
}

const openCreate = () => { editData.value = null; formVisible.value = true }
const openEdit = (row) => { editData.value = row; formVisible.value = true }

const openDetail = async (row) => {
  try {
    const { data } = await brandAuthApi.getDetail(row.id); detail.value = data
    detailVisible.value = true
    try { const r = await brandAuthApi.getLogs(row.id); detailLogs.value = r?.data || [] } catch { detailLogs.value = [] }
  } catch { ElMessage.error('加载详情失败') }
}

const showRevokeDialog = (row) => { revokeTarget.value = row; revokeVisible.value = true }

const onDetailEdit = (d) => { detailVisible.value = false; openEdit(d) }
const onDetailRevoke = (d) => { detailVisible.value = false; showRevokeDialog(d) }

const handleSave = async ({ isEdit, id, form }) => {
  try {
    let result; const fd = { ...form, authorizationType: activeTab.value === 'agent' ? 'AGENT' : 'MANUFACTURER' }
    if (isEdit) { result = await brandAuthApi.update(id, fd); ElMessage.success('更新成功') }
    else {
      result = await brandAuthApi.create(fd)
      if (result.warning) ElMessage.warning(result.warning); else ElMessage.success('创建成功')
      if (result.data?.id) {
        if (activeTab.value === 'agent') {
          if (form.auth1FileList?.length) await brandAuthApi.uploadAttachments(result.data.id, 'auth1', form.auth1FileList.map(f=>f.raw))
          if (form.auth2FileList?.length) await brandAuthApi.uploadAttachments(result.data.id, 'auth2', form.auth2FileList.map(f=>f.raw))
        } else {
          if (form.authDocFileList?.length) await brandAuthApi.uploadAttachments(result.data.id, 'AUTH_DOC', form.authDocFileList.map(f=>f.raw))
        }
        if (form.supplementaryFileList?.length) await brandAuthApi.uploadAttachments(result.data.id, 'SUPPLEMENTARY', form.supplementaryFileList.map(f=>f.raw))
      }
    }
    formVisible.value = false; editData.value = null; loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || e.message || '保存失败') }
}

const filterSummary = computed(() => {
  const p = []
  if (filters.productLines?.length) p.push('产线:' + filters.productLines.join(','))
  if (filters.brandId) p.push('品牌ID:' + filters.brandId)
  if (filters.brandName) p.push('品牌:' + filters.brandName)
  if (filters.importDomestic) p.push('进口/国产:' + filters.importDomestic)
  if (filters.manufacturerName) p.push('原厂:' + filters.manufacturerName)
  if (filters.agentName) p.push('代理商:' + filters.agentName)
  if (filters.statuses?.length) p.push('状态:' + filters.statuses.join(','))
  if (filters.keyword) p.push('关键词:' + filters.keyword)
  return p.length ? p.join('；') : '全部'
})
const exportFilename = computed(() => {
  const d = new Date(); const ts = d.toISOString().slice(0,16)
    .replace('T','_').replace(/:/g,'')
  return (activeTab.value === 'agent' ? '代理商授权清单' : '原厂授权清单') + '_' + ts + '.xlsx'
})

const importDialogRef = ref(null)
const showImport = () => { importDialogRef.value?.open() }
const onImportClose = () => {}
const onImportSuccess = () => { loadData() }

const handleExport = () => { exportVisible.value = true }

const doExport = async () => {
  if (total.value > 500) { ElMessage.warning('单次最多导出500条'); return }
  try {
    const p = new URLSearchParams()
    if (filters.productLines?.length) p.append('productLines', filters.productLines.join(','))
    if (filters.brandId) p.append('brandId', filters.brandId)
    if (filters.brandName) p.append('brandName', filters.brandName)
    if (filters.importDomestic) p.append('importDomestic', filters.importDomestic)
    if (filters.manufacturerName) p.append('manufacturerName', filters.manufacturerName)
    if (filters.agentName) p.append('agentName', filters.agentName)
    if (filters.statuses?.length) p.append('statuses', filters.statuses.join(','))
    if (filters.keyword) p.append('keyword', filters.keyword)
    const resp = await http.get('/api/knowledge/brand-auth/export?' + p.toString(), { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([resp.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = exportFilename.value
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  } catch { ElMessage.error('导出失败') }
  finally { exportVisible.value = false }
}
const onTabChange = (t) => { page.value = 1; loadData() }
onMounted(loadData)
</script>

<style scoped lang="scss">
.page-header { margin-bottom: 16px; h2 { font-weight: 600; color: #1f2937; margin: 0; } }
.tab-toolbar { display: flex; justify-content: space-between; align-items: center; margin: 12px 0; }
.filter-card, .table-card { border-radius: 8px; border: 1px solid var(--el-border-color-lighter); box-shadow: 0 2px 8px rgba(0,0,0,.05); margin-bottom: 12px; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
.revoked-tag { text-decoration: line-through; opacity: 0.6; }
</style>
