<template>
  <div class="ca-management-page">
    <!-- Statistics Cards -->
    <div v-if="isManagerView" class="stat-row">
      <el-card v-for="s in statCards" :key="s.key" class="stat-card" shadow="never">
        <div class="stat-body">
          <span class="stat-value" :style="{ color: s.color }">{{ loading ? '-' : s.value }}</span>
          <span class="stat-label">{{ s.label }}</span>
        </div>
      </el-card>
    </div>

    <!-- Search Filters -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="filters" class="search-form">
        <el-form-item label="关联平台">
          <el-input v-model="filters.platform" placeholder="平台名称" clearable style="width: 160px" @keyup.enter="applyFilters" />
        </el-form-item>
        <template v-if="isManagerView">
          <el-form-item label="CA类型">
            <el-select v-model="filters.caType" placeholder="全部" clearable style="width: 130px">
              <el-option label="实体CA" value="ENTITY_CA" />
              <el-option label="电子CA" value="ELECTRONIC_CA" />
            </el-select>
          </el-form-item>
          <el-form-item label="印章类型">
            <el-select v-model="filters.sealType" placeholder="全部" clearable style="width: 130px">
              <el-option label="公章" value="OFFICIAL_SEAL" />
              <el-option label="法人章" value="LEGAL_PERSON_SEAL" />
              <el-option label="法人签字" value="LEGAL_SIGN" />
              <el-option label="联系人签字" value="CONTACT_SIGN" />
            </el-select>
          </el-form-item>
          <el-form-item label="借用状态">
            <el-select v-model="filters.borrowStatus" placeholder="全部" clearable style="width: 130px">
              <el-option label="在库" value="IN_STOCK" />
              <el-option label="已借出" value="BORROWED" />
              <el-option label="已逾期" value="OVERDUE" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="filters.keyword" placeholder="平台/保管员/借用人" clearable style="width: 200px" @keyup.enter="applyFilters" />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" @click="applyFilters"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- CO-459: Tabs 切换（CA证书列表 / 我的申请 / 我的审批） -->
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- Tab 1: CA 证书列表 -->
        <el-tab-pane label="CA证书列表" name="certificates">
          <template #label>
            <span>CA证书列表</span>
          </template>
          <div class="tab-header">
            <div class="header-actions">
              <el-button @click="loadData"><el-icon><Refresh /></el-icon>刷新</el-button>
              <el-button v-if="canCreate" type="primary" @click="handleCreate"><el-icon><Plus /></el-icon>新增</el-button>
              <el-button v-if="canCreate" @click="showImportDialog = true"><el-icon><Upload /></el-icon>批量导入</el-button>
            </div>
          </div>

      <!-- Admin/Manager View: 10-column full view -->
      <div v-if="isManagerView" class="ca-table-wrapper">
      <el-table
        v-loading="loading"
        :data="filteredData"
        stripe
        empty-text="暂无 CA 证书数据"
        highlight-current-row
        @row-click="handleRowClick"
      >
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column label="关联平台" min-width="120">
          <template #default="{ row }">
            <span v-if="row.platformIds.length">{{ row.platformIds.join(', ') }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="CA类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.caType === 'ENTITY_CA' ? 'primary' : 'success'" size="small">
              {{ row.caTypeLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="印章" width="110">
          <template #default="{ row }">{{ row.sealTypeLabel }}</template>
        </el-table-column>
        <el-table-column label="有效期" width="110">
          <template #default="{ row }">{{ row.expiryDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="到期天数" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.remainingDays < 0" class="text-danger">{{ row.remainingDays }}天</span>
            <span v-else-if="row.remainingDays <= 30" class="text-warning">剩{{ row.remainingDays }}天</span>
            <span v-else-if="row.remainingDays && row.remainingDays < Infinity" class="text-success">{{ row.remainingDays }}天</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="caStatusTagType(row.status)" size="small" class="status-tag">
              {{ row.statusLabel }}
            </el-tag>
            <el-tag :type="caBorrowStatusTagType(row.borrowStatus)" size="small" class="status-tag">
              {{ row.borrowStatusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="借用人" min-width="100">
          <template #default="{ row }">
            <span v-if="row.borrowStatus === 'BORROWED'">{{ row.currentBorrowerName || '-' }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="保管员" min-width="100">
          <!-- CO-451: 保管员显示为"姓名（工号）"格式 -->
          <template #default="{ row }">{{ formatDisplayName(row.custodianName, row.custodianEmployeeNumber) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="handleView(row)">查看</el-button>
            <el-button v-if="canManage(row)" link type="primary" size="small" @click.stop="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="canBorrow(row)"
              link type="success" size="small"
              @click.stop="handleOpenBorrow(row)"
            >借用</el-button>
            <el-button
              v-if="canReturn(row)"
              link type="warning" size="small"
              @click.stop="handleOpenReturn(row)"
            >登记归还</el-button>
            <el-button v-if="canManage(row)" link type="danger" size="small" @click.stop="handleDelete(row)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- Default View: 5-column simplified view -->
      <div v-else class="ca-table-wrapper">
      <el-table
        v-loading="loading"
        :data="filteredData"
        stripe
        empty-text="暂无 CA 证书数据"
      >
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column label="关联平台" min-width="140">
          <template #default="{ row }">
            <span v-if="row.platformIds.length">{{ row.platformIds.join(', ') }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="CA类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.caType === 'ENTITY_CA' ? 'primary' : 'success'" size="small">
              {{ row.caTypeLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="印章" width="110">
          <template #default="{ row }">{{ row.sealTypeLabel }}</template>
        </el-table-column>
        <el-table-column label="保管员" min-width="100">
          <!-- CO-451: 保管员显示为"姓名（工号）"格式 -->
          <template #default="{ row }">{{ formatDisplayName(row.custodianName, row.custodianEmployeeNumber) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.borrowStatus === 'IN_STOCK' && row.caType === 'ENTITY_CA' && row.status !== 'EXPIRED' && row.status !== 'INACTIVE'"
              type="primary" size="small"
              @click="handleOpenBorrow(row)"
            >申请使用</el-button>
            <span v-else-if="row.caType === 'ELECTRONIC_CA'" class="text-muted">电子CA无需借用</span>
            <el-tag v-else-if="row.borrowStatus === 'BORROWED'" type="primary" size="small">已借出</el-tag>
            <el-tag v-else type="info" size="small">不可借用</el-tag>
          </template>
        </el-table-column>
      </el-table>
      </div>
        </el-tab-pane>

        <!-- CO-459: Tab 2: 我的申请 -->
        <el-tab-pane label="我的申请" name="myApplications">
          <el-table
            v-loading="myApplicationsLoading"
            :data="myApplications"
            stripe
            empty-text="暂无借用申请"
          >
            <el-table-column type="index" label="序号" width="70" />
            <el-table-column label="关联CA" min-width="140">
              <template #default="{ row }">{{ row.caName || `CA#${row.caCertificateId}` }}</template>
            </el-table-column>
            <el-table-column label="借用时间" min-width="120">
              <template #default="{ row }">{{ row.borrowDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="预计归还" min-width="120">
              <template #default="{ row }">{{ row.expectedReturnDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="applicationStatusType(row.status)" size="small">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING_APPROVAL'" link type="danger" size="small" @click="handleCancelApplication(row)">撤销</el-button>
                <span v-else class="op-placeholder">--</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- CO-459: Tab 3: 我的审批 -->
        <el-tab-pane label="我的审批" name="myApprovals">
          <el-table
            v-loading="myApprovalsLoading"
            :data="myApprovals"
            stripe
            empty-text="暂无待审批申请"
          >
            <el-table-column type="index" label="序号" width="70" />
            <el-table-column label="关联CA" min-width="140">
              <template #default="{ row }">{{ row.caName || `CA#${row.caCertificateId}` }}</template>
            </el-table-column>
            <el-table-column label="申请人" min-width="100">
              <template #default="{ row }">{{ row.applicantName || '-' }}</template>
            </el-table-column>
            <el-table-column label="借用时间" min-width="120">
              <template #default="{ row }">{{ row.borrowDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="预计归还" min-width="120">
              <template #default="{ row }">{{ row.expectedReturnDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="applicationStatusType(row.status)" size="small">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING_APPROVAL'">
                  <el-button link type="primary" size="small" @click="handleApproveApplication(row)">通过</el-button>
                  <el-button link type="danger" size="small" @click="handleRejectApplication(row)">拒绝</el-button>
                </template>
                <el-button v-else-if="row.status === 'APPROVED' && row.borrowDurationType !== 'LONG_TERM'" link type="warning" size="small" @click="handleOpenReturnFromApproval(row)">登记归还</el-button>
                <span v-else class="op-placeholder">--</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- Detail Dialog -->
    <CADetailDialog
      v-model="drawerVisible"
      :ca="selectedCa"
      :borrow-applications="borrowApplications"
      :operation-events="operationEvents"
      :actions="detailActions"
      @edit="handleEdit"
      @borrow="handleOpenBorrow"
      @return="handleOpenReturn"
    />

    <!-- Form Dialog -->
    <CAFormDialog
      v-model="formVisible"
      :ca="editingCa"
      :submitting="formSubmitting"
      @submit="handleFormSubmit"
    />

    <!-- Borrow Dialog -->
    <CABorrowDialog
      v-model="borrowVisible"
      :ca="selectedCa"
      :submitting="borrowSubmitting"
      :projects="projectOptions"
      :upload-url="borrowUploadUrl"
      :upload-headers="borrowUploadHeaders"
      @submit="handleBorrowSubmit"
    />

    <!-- Return Dialog -->
    <CAReturnDialog
      v-model="returnVisible"
      :ca="selectedCa"
      :borrow-applications="borrowApplicationsForReturn"
      :submitting="returnSubmitting"
      @submit="handleReturnSubmit"
    />
    <CAImportDialog v-model="showImportDialog" @imported="loadData" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh, Plus, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useCaStore } from '@/stores/ca'
import { caApi } from '@/api/modules/ca'
import { projectsApi } from '@/api'
import httpClient from '@/api/client'
import { isBidManager } from '@/utils/permission'
import { useCaBorrowEligibility, caStatusTagType, caBorrowStatusTagType } from './composables/useCaBorrowEligibility'
import { formatDisplayName } from '@/utils/formatDisplayName'
import CADetailDialog from './components/CADetailDialog.vue'
import CAFormDialog from './components/CAFormDialog.vue'
import CABorrowDialog from './components/CABorrowDialog.vue'
import CAReturnDialog from './components/CAReturnDialog.vue'
import CAImportDialog from './components/CAImportDialog.vue'

const userStore = useUserStore()
const caStore = useCaStore()

const { canBorrow, canManage, canReturn } = useCaBorrowEligibility()

const projectOptions = ref([])

// CO-459: Tab 状态
const activeTab = ref('certificates')
const myApplications = ref([])
const myApprovals = ref([])
const myApplicationsLoading = ref(false)
const myApprovalsLoading = ref(false)

const borrowUploadUrl = computed(() => {
  const base = httpClient.defaults?.baseURL || ''
  return `${base}/api/ca-certificates/commitment-letter/upload`
})
const borrowUploadHeaders = computed(() => ({
  Authorization: userStore.token ? `Bearer ${userStore.token}` : ''
}))

// Role-based view determination
// CO-409: 投标专员（bid-Team）进入完整管理员视图（10 列 + 统计 + 高级筛选），
// 操作项按保管员差异化（canManageRow/canBorrowRow），对齐 CO-409 权限矩阵。
// CO-393 注：bid-projectLeader 虽持 resource-ca 路由权限，但不在 isBidManager 内，仍走简化视图。
const isManagerView = computed(() => isBidManager(userStore.userRole) || userStore.userRole === 'bid-Team')

// CO-409: 新增/批量导入操作项对投标专员（bid-Team）放开，与管理员一致。
const canCreate = computed(() => isBidManager(userStore.userRole) || userStore.userRole === 'bid-Team')

// Loading states
const loading = ref(false)
const formSubmitting = ref(false)
const borrowSubmitting = ref(false)
const returnSubmitting = ref(false)

// Data
const overview = reactive({ total: 0, expiring: 0, expired: 0, borrowed: 0 })

// Filters
const filters = reactive({
  platform: '',
  caType: '',
  sealType: '',
  borrowStatus: '',
  keyword: ''
})
const appliedFilters = reactive({
  platform: '',
  caType: '',
  sealType: '',
  borrowStatus: '',
  keyword: ''
})

// Dialog/Drawer visibility
const drawerVisible = ref(false)
const formVisible = ref(false)
const borrowVisible = ref(false)
const returnVisible = ref(false)
const showImportDialog = ref(false)

// Selected item
const selectedCa = ref(null)
const editingCa = ref(null)
const borrowApplications = ref([])
const borrowApplicationsForReturn = ref([])
const operationEvents = ref([])

const detailActions = computed(() => {
  const ca = selectedCa.value
  if (!ca) return { canEdit: false, canBorrow: false, canReturn: false }
  return {
    canEdit: canManage(ca),
    canBorrow: canBorrow(ca),
    canReturn: canReturn(ca)
  }
})

// Stat cards config
const statCards = computed(() => [
  { key: 'total', label: '总数', value: overview.total, color: 'var(--el-color-primary)' },
  { key: 'expiring', label: '即将到期', value: overview.expiring, color: 'var(--el-color-warning)' },
  { key: 'expired', label: '已过期', value: overview.expired, color: 'var(--el-color-danger)' },
  { key: 'borrowed', label: '已借出', value: overview.borrowed, color: 'var(--el-color-info)' }
])

// Filtered data (client-side filtering)
const filteredData = computed(() => {
  const f = appliedFilters
  let list = caStore.certificates

  if (f.platform) {
    const kw = f.platform.toLowerCase()
    list = list.filter(c => c.platformIds.some(p => String(p).toLowerCase().includes(kw)))
  }
  if (f.caType) {
    list = list.filter(c => c.caType === f.caType)
  }
  if (f.sealType) {
    list = list.filter(c => c.sealType === f.sealType)
  }
  if (f.borrowStatus) {
    list = list.filter(c => c.borrowStatus === f.borrowStatus)
  }
  if (f.keyword) {
    const kw = f.keyword.toLowerCase()
    list = list.filter(c =>
      c.platformIds.some(p => String(p).toLowerCase().includes(kw)) ||
      (c.custodianName || '').toLowerCase().includes(kw) ||
      (c.currentBorrowerName || '').toLowerCase().includes(kw) ||
      (c.caPlatformUrl || '').toLowerCase().includes(kw)
    )
  }

  return list
})

// Data loading
async function loadData() {
  loading.value = true
  try {
    await caStore.loadCertificates({ size: 500 })
    await loadOverview()
  } catch {
    ElMessage.error('加载 CA 证书数据失败')
  } finally {
    loading.value = false
  }
}

async function loadOverview() {
  try {
    const result = await caApi.getOverview()
    if (result?.data) {
      overview.total = result.data.total ?? 0
      overview.expiring = result.data.expiring ?? 0
      overview.expired = result.data.expired ?? 0
      overview.borrowed = result.data.borrowed ?? 0
    }
  } catch {
    // Overview is non-critical
  }
}

// Filters
function applyFilters() {
  Object.assign(appliedFilters, { ...filters })
}

function resetFilters() {
  Object.assign(filters, { platform: '', caType: '', sealType: '', borrowStatus: '', keyword: '' })
  Object.assign(appliedFilters, { platform: '', caType: '', sealType: '', borrowStatus: '', keyword: '' })
}

// Row click opens detail (admin/manager only)
function handleRowClick(row) {
  if (isManagerView.value) {
    handleView(row)
  }
}

// View detail
async function handleView(ca) {
  selectedCa.value = ca
  borrowApplications.value = []
  operationEvents.value = []
  drawerVisible.value = true

  // Load borrow applications
  try {
    const res = await caApi.getBorrowApplications(ca.id)
    borrowApplications.value = res?.data || []
  } catch { /* non-critical */ }

  // Load operation events if any borrow application exists
  if (borrowApplications.value.length > 0) {
    try {
      const eventsRes = await caApi.getOperationEvents(borrowApplications.value[0].id)
      operationEvents.value = eventsRes?.data || []
    } catch { /* non-critical */ }
  }
}

// CRUD operations
function handleCreate() {
  editingCa.value = null
  formVisible.value = true
}

function handleEdit(ca) {
  editingCa.value = { ...ca }
  formVisible.value = true
  drawerVisible.value = false
}

async function handleFormSubmit(formData) {
  formSubmitting.value = true
  try {
    if (formData.id) {
      await caStore.updateCertificate(formData.id, formData)
      ElMessage.success('更新成功')
    } else {
      await caStore.createCertificate(formData)
      ElMessage.success('创建成功')
    }
    formVisible.value = false
    await loadData()
  } catch {
    ElMessage.error(formData.id ? '更新失败' : '创建失败')
  } finally {
    formSubmitting.value = false
  }
}

async function handleDelete(ca) {
  try {
    await ElMessageBox.confirm(
      `确认下架该 CA 证书？此操作不可恢复。`,
      '下架确认',
      { type: 'warning' }
    )
  } catch {
    // CO-441: 用户点击遮罩层/取消/关闭，静默退出，避免 reject('cancel') 未捕获触发 ErrorBoundary
    return
  }
  try {
    await caStore.deactivateCertificate(ca.id)
    ElMessage.success('已下架')
    await loadData()
  } catch {
    ElMessage.error('下架失败')
  }
}

// Borrow flow
function handleOpenBorrow(ca) {
  selectedCa.value = ca
  borrowVisible.value = true
}

async function handleBorrowSubmit(borrowData) {
  borrowSubmitting.value = true
  try {
    await caStore.borrowCertificate(selectedCa.value.id, borrowData)
    ElMessage.success('借用申请已提交')
    borrowVisible.value = false
    await loadData()
  } catch {
    ElMessage.error('借用申请提交失败')
  } finally {
    borrowSubmitting.value = false
  }
}

// Return flow
async function handleOpenReturn(ca) {
  selectedCa.value = ca
  borrowApplicationsForReturn.value = []
  returnVisible.value = true

  // Load borrow applications to find active one
  try {
    const res = await caApi.getBorrowApplications(ca.id)
    borrowApplicationsForReturn.value = res?.data || []
  } catch { /* non-critical */ }
}

async function handleReturnSubmit(returnData) {
  returnSubmitting.value = true
  try {
    await caStore.returnCertificate(returnData.applicationId, returnData)
    ElMessage.success('归还成功')
    returnVisible.value = false
    await loadData()
    drawerVisible.value = false
  } catch {
    ElMessage.error('归还失败')
  } finally {
    returnSubmitting.value = false
  }
}

async function loadProjects() {
  try {
    const res = await projectsApi.getList({})
    const list = Array.isArray(res?.data) ? res.data : []
    projectOptions.value = list
  } catch { projectOptions.value = [] }
}

// CO-459: Tab 切换处理
async function onTabChange(tabName) {
  if (tabName === 'myApplications') {
    await loadMyApplications()
  } else if (tabName === 'myApprovals') {
    await loadMyApprovals()
  }
}

async function loadMyApplications() {
  myApplicationsLoading.value = true
  try {
    const res = await caApi.getMyBorrowApplications()
    myApplications.value = res?.data || []
  } catch {
    ElMessage.error('加载我的借用申请失败')
    myApplications.value = []
  } finally {
    myApplicationsLoading.value = false
  }
}

async function loadMyApprovals() {
  myApprovalsLoading.value = true
  try {
    const res = await caApi.getMyApprovals()
    myApprovals.value = res?.data || []
  } catch {
    ElMessage.error('加载我的审批列表失败')
    myApprovals.value = []
  } finally {
    myApprovalsLoading.value = false
  }
}

// CO-459: 申请状态标签类型
function applicationStatusType(status) {
  switch (status) {
    case 'PENDING_APPROVAL': return 'warning'
    case 'APPROVED': return 'success'
    case 'REJECTED': return 'danger'
    case 'RETURNED': return 'info'
    case 'CANCELLED': return 'info'
    default: return ''
  }
}

// CO-459: 撤销申请
async function handleCancelApplication(row) {
  try {
    await ElMessageBox.confirm('确认撤销该借用申请？', '撤销确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await caStore.cancelBorrow(row.id)
    ElMessage.success('已撤销')
    await loadMyApplications()
  } catch {
    ElMessage.error('撤销失败')
  }
}

// CO-459: 审批通过
async function handleApproveApplication(row) {
  try {
    await ElMessageBox.confirm('确认通过该借用申请？', '审批确认', { type: 'success' })
  } catch {
    return
  }
  try {
    await caStore.approveApplication(row.id, '')
    ElMessage.success('审批通过')
    await loadMyApprovals()
  } catch {
    ElMessage.error('审批失败')
  }
}

// CO-459: 审批拒绝
async function handleRejectApplication(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝申请', {
      inputPattern: /.+/,
      inputErrorMessage: '拒绝原因不能为空',
      inputType: 'textarea'
    })
    await caStore.rejectApplication(row.id, value)
    ElMessage.success('已拒绝')
    await loadMyApprovals()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('拒绝操作失败')
    }
  }
}

// CO-459: 从审批列表登记归还
async function handleOpenReturnFromApproval(row) {
  selectedCa.value = { id: row.caCertificateId, platformIds: row.caName ? [row.caName] : [] }
  borrowApplicationsForReturn.value = [row]
  returnVisible.value = true
}

onMounted(() => {
  loadData()
  loadProjects()
})
</script>

<style scoped>
.ca-management-page {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 表头强制单行显示，参照 Project/List.vue 标准方案（历史沉淀）。
   关键：只作用到 th 的直接子 .cell，并加 overflow:visible + text-overflow:clip，
   否则 .cell 默认 overflow:hidden 会把超出列宽的单行文字截断导致看不到标题。 */
.ca-table-wrapper { overflow-x: auto; }
.ca-table-wrapper :deep(.el-table th.el-table__cell > .cell) {
  white-space: nowrap;
  overflow: visible;
  text-overflow: clip;
  padding-right: 20px;
  position: relative;
}
/* td cell 也允许溢出可见，避免 el-tag/短文本被 .cell 默认 overflow:hidden 截断。
   保留 white-space:normal 允许长文本换行，不撑爆列宽。 */
.ca-table-wrapper :deep(.el-table td.el-table__cell > .cell) {
  overflow: visible;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  text-align: center;
  height: 100%;
  align-self: start;
}

.stat-card :deep(.el-card__body) {
  padding: 16px;
  height: 100%;
  box-sizing: border-box;
}

.stat-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
}

.stat-label {
  font-size: 14px;
  color: var(--text-muted, #909399);
  margin-top: 4px;
}

.search-form {
  margin-bottom: -18px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

/* CO-459: Tab header 样式 */
.tab-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.op-placeholder {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.status-tag {
  margin-right: 4px;
}

.text-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}

.text-warning {
  color: var(--el-color-warning);
  font-weight: 600;
}

.text-success {
  color: var(--el-color-success);
}

.text-muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 768px) {
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
